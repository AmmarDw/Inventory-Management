package com.speedit.inventorysystem.service;

import com.speedit.inventorysystem.dto.allocation.CandidateGenerationResultDto;
import com.speedit.inventorysystem.dto.allocation.CandidateMetricsDto;
import com.speedit.inventorysystem.dto.allocation.PathCandidateDto;
import com.speedit.inventorysystem.dto.ors.OrsRouteResponse;
import com.speedit.inventorysystem.enums.InventoryTypeEnum;
import com.speedit.inventorysystem.enums.MovementStatus;
import com.speedit.inventorysystem.enums.MovementType;
import com.speedit.inventorysystem.model.*;
import com.speedit.inventorysystem.service.StockMonitoringService;
import com.speedit.inventorysystem.repository.InventoryRepository;
import com.speedit.inventorysystem.repository.InventoryStockRepository;
import com.speedit.inventorysystem.repository.StockMovementRepository;
import com.speedit.inventorysystem.dto.Coordinates;
import com.speedit.inventorysystem.dto.RouteDetails;
import com.speedit.inventorysystem.service.RoutingService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CandidateGeneratorService {

    private final InventoryStockRepository inventoryStockRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final RoutingService routingService;
    private final StockMonitoringService stockMonitoringService;

    // configuration constants
    private static final int MAX_CANDIDATES_PER_ITEM = 5;
    private static final double DEFAULT_UNIT_VOLUME_CC = 1000.0;
    private static final double MIN_SPLIT_RATIO = 0.2;   // 20% of requested quantity
    private static final int    MIN_SPLIT_ABSOLUTE = 5;  // 5 units
    // Working hours 08:00–17:00 (Riyadh)
    private static final LocalTime WORK_START = LocalTime.of(8, 0);
    private static final LocalTime WORK_END   = LocalTime.of(17, 0);
    private static final DayOfWeek WEEKEND    = DayOfWeek.FRIDAY;

    /**
     * Phase A: generate candidate paths for a single OrderItem.
     * This method is READ-ONLY (no DB writes).
     */
    @Transactional
    public CandidateGenerationResultDto generateCandidatesForOrderItem(Order order, OrderItem orderItem) {

        Objects.requireNonNull(order, "order must not be null");
        Objects.requireNonNull(orderItem, "orderItem must not be null");

        int requestedQty = orderItem.getQuantity();
        if (requestedQty <= 0) {
            return emptyResult(order, orderItem);
        }

        // unit volume in cc
        BigDecimal volume = orderItem.getProduct().getVolume();
        double unitVolumeCc = (volume != null ? volume.doubleValue() : DEFAULT_UNIT_VOLUME_CC);

        Coordinates clientCoords = new Coordinates(
                order.getLatitude().doubleValue(),
                order.getLongitude().doubleValue()
        );

        // 1) Load available stocks for this product from warehouses and vans
        List<InventoryTypeEnum> allowedTypes = Arrays.asList(
                InventoryTypeEnum.WAREHOUSE,
                InventoryTypeEnum.VAN
        );

        List<InventoryStock> availableStocks =
                inventoryStockRepository.findAvailableByProductAndTypes(
                        orderItem.getProduct().getProductId(),
                        allowedTypes
                );

        if (availableStocks.isEmpty()) {
            return emptyResult(order, orderItem);
        }

        // Build a map of van stocks for this product (for pattern 2 awareness)
        Map<Integer, InventoryStock> vanProductStockMap = availableStocks.stream()
                .filter(s -> s.getInventory().getInventoryType() == InventoryTypeEnum.VAN)
                .collect(Collectors.toMap(
                        s -> s.getInventory().getInventoryId(),
                        s -> s
                ));

        List<PathCandidateDto> candidates = new ArrayList<>();

        for (InventoryStock stock : availableStocks) {
            Inventory inv = stock.getInventory();

            if (inv.getInventoryType() == InventoryTypeEnum.VAN) {
                // Pattern 1: VAN -> CLIENT
                Optional<PathCandidateDto> vanCandidate =
                        buildVanDirectCandidate(order, orderItem, stock, clientCoords, unitVolumeCc);
                vanCandidate.ifPresent(candidates::add);
            }

            if (inv.getInventoryType() == InventoryTypeEnum.WAREHOUSE) {
                // Skip warehouses in different cities than client (for now)
                Coordinates whCoords = new Coordinates(inv.getLatitude().doubleValue(), inv.getLongitude().doubleValue());
                if (routingService.isInDifferentCities(whCoords, clientCoords)) {
                    // TODO: consider far WH -> near WH multi-hop pattern in the future
                    continue;
                }

                // Pattern 2: WAREHOUSE (same city) -> VAN -> CLIENT
                List<PathCandidateDto> whCandidates =
                        buildWarehouseToVanCandidates(order, orderItem, stock, clientCoords, unitVolumeCc, vanProductStockMap);
                candidates.addAll(whCandidates);
            }
        }

        if (candidates.isEmpty()) {
            return emptyResult(order, orderItem);
        }

        // 2) Compute global max distance/time for normalization
        double maxDistKm = candidates.stream()
                .mapToDouble(c -> c.getMetrics().getDistanceKm())
                .max()
                .orElse(1.0);

        long maxTimeSec = candidates.stream()
                .mapToLong(c -> c.getMetrics().getTravelTimeSec())
                .max()
                .orElse(1L);

        // 3) Compute provisional scores with split penalty relative to requested quantity
        for (PathCandidateDto c : candidates) {
            computeProvisionalScore(c, requestedQty, maxDistKm, maxTimeSec);
        }

        // 4) Sort & dedupe by primaryInventoryStock (avoid overlapping usage of same stock)
        List<PathCandidateDto> sorted = candidates.stream()
                .sorted(Comparator.comparingDouble(PathCandidateDto::getProvisionalScore))
                .collect(Collectors.toList());

        List<PathCandidateDto> topUnique = new ArrayList<>();
        Set<Integer> usedInventoryStockIds = new HashSet<>();

        for (PathCandidateDto c : sorted) {
            Integer stockId = c.getPrimaryInventoryStock().getInventoryStockId();
            if (usedInventoryStockIds.contains(stockId)) {
                continue;
            }
            topUnique.add(c);
            usedInventoryStockIds.add(stockId);
            if (topUnique.size() >= MAX_CANDIDATES_PER_ITEM) {
                break;
            }
        }

        return CandidateGenerationResultDto.builder()
                .orderId(order.getOrderId())
                .orderItemId(orderItem.getOrderItemId())
                .productId(orderItem.getProduct().getProductId())
                .requestedQuantity(requestedQty)
                .candidates(topUnique)
                .build();
    }

    // ----------------- Pattern 1: VAN -> CLIENT -----------------

    private Optional<PathCandidateDto> buildVanDirectCandidate(
            Order order,
            OrderItem item,
            InventoryStock vanStock,
            Coordinates clientCoords,
            double unitVolumeCc
    ) {
        Inventory van = vanStock.getInventory();

        // 1) find van current location
        Coordinates vanCurrentCoords = resolveVanCurrentCoordinates(van);

        // 2) ensure same city
        if (routingService.isInDifferentCities(vanCurrentCoords, clientCoords)) {
            return Optional.empty();
        }

        // 3) compute route overhead (add client as new stop)
        List<Coordinates> originalStops = buildVanOriginalStopsForOverhead(van, vanCurrentCoords);
        RouteDetails overhead = routingService.calculateShortestPathOverhead(originalStops, clientCoords);

        double distanceKm = overhead.getDistanceInMeters() / 1000.0;
        long travelTimeSec = (long) overhead.getDurationInSeconds();

        // 4) compute how many units we can deliver (bounded by stock & van capacity)
        int availableUnits = vanStock.getAmount();
        int feasibleByVan = computeMaxUnitsForVan(van, unitVolumeCc, availableUnits);
        if (feasibleByVan <= 0) {
            return Optional.empty();
        }

        int maxFeasibleAmount = feasibleByVan;

        // 5) handling time: unloading at client only (example: 5 min = 300 s)
        double handlingTimeSec = 300.0;

        // 6) maxPressure is 0 here because we are only unloading stock already in van
        //    (pressure gets LOWER, not higher)
        CandidateMetricsDto metrics = CandidateMetricsDto.builder()
                .distanceKm(distanceKm)
                .travelTimeSec(travelTimeSec)
                .handlingTimeSec(handlingTimeSec)
                .maxPressure(0.0)
                .build();

        // 7) create in-memory StockMovement (van -> client)
        OffsetDateTime moveAt = computeNextWorkingTimeForVan(van, travelTimeSec);

        StockMovement move = StockMovement.builder()
                .inventoryStock(vanStock)
                .fromInventory(van)
                .toInventory(null) // client
                .movementType(MovementType.UNLOAD)
                .movementStatus(MovementStatus.PLANNED)
                .moveAt(moveAt)
                .estimatedVolumeCc(BigDecimal.valueOf(maxFeasibleAmount * unitVolumeCc))
                .assignedEmployee(null)
                .build();

        PathCandidateDto candidate = PathCandidateDto.builder()
                .primaryInventoryStock(vanStock)
                .productId(item.getProduct().getProductId())
                .deliveringVan(van)
                .maxFeasibleAmount(maxFeasibleAmount)
                .movements(Collections.singletonList(move))
                .metrics(metrics)
                .pattern("VAN->CLIENT")
                .build();

        return Optional.of(candidate);
    }

    // ----------------- Pattern 2: WH (same city) -> VAN -> CLIENT -----------------

    private List<PathCandidateDto> buildWarehouseToVanCandidates(
            Order order,
            OrderItem item,
            InventoryStock warehouseStock,
            Coordinates clientCoords,
            double unitVolumeCc,
            Map<Integer, InventoryStock> vanProductStockMap
    ) {
        List<PathCandidateDto> result = new ArrayList<>();

        Inventory wh = warehouseStock.getInventory();

        // active vans (we let scoring choose the best ones later)
        List<Inventory> activeVans = inventoryRepository.findActiveByType(InventoryTypeEnum.VAN);
        if (activeVans.isEmpty()) {
            return result;
        }

        for (Inventory van : activeVans) {
            Coordinates vanCoords = resolveVanCurrentCoordinates(van);

            // we want vans in same city as client
            if (routingService.isInDifferentCities(vanCoords, clientCoords)) {
                continue;
            }

            // compute overhead for new route: van -> warehouse -> client (+ existing stops)
            List<Coordinates> originalStops = buildVanOriginalStopsForOverhead(van, vanCoords);

            // Overhead from van current to warehouse
            Coordinates whCoords = new Coordinates(wh.getLatitude().doubleValue(), wh.getLongitude().doubleValue());
            RouteDetails overheadToWh = routingService.calculateShortestPathOverhead(originalStops, whCoords);

            // After adding warehouse, we recompute list of stops including warehouse and then add client
            List<Coordinates> stopsPlusWh = new ArrayList<>(originalStops);
            stopsPlusWh.add(whCoords);
            RouteDetails overheadWhToClient = routingService.calculateShortestPathOverhead(stopsPlusWh, clientCoords);

            double distanceKm = (overheadToWh.getDistanceInMeters() + overheadWhToClient.getDistanceInMeters()) / 1000.0;
            long travelTimeSec = (long) (overheadToWh.getDurationInSeconds() + overheadWhToClient.getDurationInSeconds());

            // consider order quantity, warehouse stock, and van capacity
            int orderQty = item.getQuantity();
            int whAvailable = warehouseStock.getAmount();
            InventoryStock vanProductStock = vanProductStockMap.get(van.getInventoryId());
            int vanAvailable = (vanProductStock != null ? vanProductStock.getAmount() : 0);

            // If van alone already has enough to cover the order, we expect a VAN->CLIENT candidate
            // to be strictly better, so we skip this WH->VAN pattern for this van.
            if (vanAvailable >= orderQty) {
                continue;
            }

            int desiredUnits = Math.min(orderQty, vanAvailable + whAvailable);
            int feasibleForVan = computeMaxUnitsForVan(van, unitVolumeCc, desiredUnits);
            if (feasibleForVan <= 0) {
                continue;
            }

            int maxFeasibleAmount = feasibleForVan;

            // handling time: two load/unload operations (example: 600 s)
            double handlingTimeSec = 600.0;

            // compute maxPressure on van: current fill + added load
            double maxPressure = computeMaxPressureForVan(van, maxFeasibleAmount, unitVolumeCc);

            CandidateMetricsDto metrics = CandidateMetricsDto.builder()
                    .distanceKm(distanceKm)
                    .travelTimeSec(travelTimeSec)
                    .handlingTimeSec(handlingTimeSec)
                    .maxPressure(maxPressure)
                    .build();

            // Movements: WH -> VAN, then VAN -> CLIENT
            OffsetDateTime firstMoveAt = computeNextWorkingTimeForVan(van, travelTimeSec / 2); // rough first leg time

            StockMovement move1 = StockMovement.builder()
                    .inventoryStock(warehouseStock)
                    .fromInventory(wh)
                    .toInventory(van)
                    .movementType(MovementType.LOAD)
                    .movementStatus(MovementStatus.PLANNED)
                    .moveAt(firstMoveAt)
                    .estimatedVolumeCc(BigDecimal.valueOf(maxFeasibleAmount * unitVolumeCc))
                    .assignedEmployee(null)
                    .build();

            OffsetDateTime secondMoveAt = shiftIntoWorkingHours(firstMoveAt.plusSeconds(travelTimeSec));

            StockMovement move2 = StockMovement.builder()
                    .inventoryStock(warehouseStock) // stock that we moved from WH and now resides in van
                    .fromInventory(van)
                    .toInventory(null) // client
                    .movementType(MovementType.UNLOAD)
                    .movementStatus(MovementStatus.PLANNED)
                    .moveAt(secondMoveAt)
                    .estimatedVolumeCc(BigDecimal.valueOf(maxFeasibleAmount * unitVolumeCc))
                    .assignedEmployee(null)
                    .build();

            PathCandidateDto candidate = PathCandidateDto.builder()
                    .primaryInventoryStock(warehouseStock)
                    .productId(item.getProduct().getProductId())
                    .deliveringVan(van)
                    .maxFeasibleAmount(maxFeasibleAmount)
                    .movements(Arrays.asList(move1, move2))
                    .metrics(metrics)
                    .pattern("WH->VAN->CLIENT")
                    .build();

            result.add(candidate);
        }

        return result;
    }

    // ----------------- Scoring -----------------

    private void computeProvisionalScore(
            PathCandidateDto candidate,
            int requestedQty,
            double maxDistKm,
            long maxTimeSec
    ) {
        CandidateMetricsDto m = candidate.getMetrics();

        double normDist = (maxDistKm > 0) ? Math.min(m.getDistanceKm() / maxDistKm, 1.0) : 0.0;
        double normTime = (maxTimeSec > 0) ? Math.min((double) m.getTravelTimeSec() / maxTimeSec, 1.0) : 0.0;
        double normHandling = Math.min(m.getHandlingTimeSec() / 600.0, 1.0); // 10 min cap for normalization
        double normPressure = Math.min(m.getMaxPressure(), 1.0);

        // split penalty based on ratio
        double ratio = (requestedQty > 0)
                ? (double) candidate.getMaxFeasibleAmount() / (double) requestedQty
                : 1.0;

        boolean tinySplit = candidate.getMaxFeasibleAmount() > 0
                && (candidate.getMaxFeasibleAmount() < MIN_SPLIT_ABSOLUTE || ratio < MIN_SPLIT_RATIO);

        double splitPenalty = tinySplit ? 1.0 : 0.0;

        // weights
        double wTime = 1.0;
        double wDist = 0.5;
        double wHandling = 0.7;
        double wPress = 0.8;
        double wSplit = 1.5;

        double score =
                wTime * normTime +
                        wDist * normDist +
                        wHandling * normHandling +
                        wPress * normPressure +
                        wSplit * splitPenalty;

        candidate.setProvisionalScore(score);
    }

    // ----------------- Capacity & pressure helpers -----------------

    private int computeMaxUnitsForVan(Inventory van, double unitVolumeCc, int desiredUnits) {
        if (unitVolumeCc <= 0 || desiredUnits <= 0) return 0;

        double capacityCc = van.getCapacity().doubleValue();
        double currentFill = stockMonitoringService.calculateFillLevel(van).get("fillLevelRatio").doubleValue(); // 0..1
        double usedVolumeCc = currentFill * capacityCc;
        double freeVolumeCc = Math.max(0.0, capacityCc - usedVolumeCc);

        int capacityByVolume = (int) Math.floor(freeVolumeCc / unitVolumeCc);
        return Math.max(0, Math.min(desiredUnits, capacityByVolume));
    }

    private double computeMaxPressureForVan(Inventory van, int addedUnits, double unitVolumeCc) {
        if (addedUnits <= 0 || unitVolumeCc <= 0) return stockMonitoringService.calculateFillLevel(van).get("fillLevelRatio").doubleValue();
        double baseFill = stockMonitoringService.calculateFillLevel(van).get("fillLevelRatio").doubleValue();
        double capacityCc = van.getCapacity().doubleValue();
        double addedVolume = addedUnits * unitVolumeCc;
        double addedFraction = addedVolume / capacityCc;
        return Math.min(1.0, baseFill + addedFraction);
    }

    // ----------------- Van location & scheduling helpers -----------------

    private Coordinates resolveVanCurrentCoordinates(Inventory van) {
        // 1) try latest DONE movement
        List<StockMovement> latestDone = stockMovementRepository
                .findLatestByInventoryAndStatus(van, MovementStatus.DONE);

        if (latestDone.isEmpty()) {
            // if no history, assume van is at its "home" inventory location
            return new Coordinates(van.getLatitude().doubleValue(), van.getLongitude().doubleValue());
        }

        StockMovement lastMovement = latestDone.get(0);
        OffsetDateTime lastTime = lastMovement.getMoveAt();

        Coordinates lastCoordinates = extractCoordinatesForMovementEndpoint(lastMovement);

        // 2) find upcoming movement (if any)
        List<StockMovement> future = stockMovementRepository
                .findFutureByInventory(van, OffsetDateTime.now(ZoneOffset.UTC));

        if (future.isEmpty()) {
            return lastCoordinates;
        }

        StockMovement nextMovement = future.get(0);
        Coordinates nextCoordinates = extractCoordinatesForMovementOtherSide(nextMovement, van);

        OrsRouteResponse routeData = routingService.getFullRouteData(lastCoordinates, nextCoordinates);

        double elapsed = Duration.between(lastTime, OffsetDateTime.now(ZoneOffset.UTC))
                .plusMinutes(5) // extra safety margin
                .toMillis() / 1000.0;

        return routingService.findLocationAfterDuration(routeData, elapsed);
    }

    private Coordinates extractCoordinatesForMovementEndpoint(StockMovement movement) {
        Inventory inv = (movement.getToInventory() != null) ? movement.getToInventory() : movement.getFromInventory();
        if (inv == null) {
            // fallback to stock's inventory as last location
            inv = movement.getInventoryStock().getInventory();
        }
        return new Coordinates(inv.getLatitude().doubleValue(), inv.getLongitude().doubleValue());
    }

    private Coordinates extractCoordinatesForMovementOtherSide(StockMovement movement, Inventory known) {
        Inventory other;
        if (movement.getFromInventory() != null && movement.getFromInventory().getInventoryId().equals(known.getInventoryId())) {
            other = movement.getToInventory();
        } else {
            other = movement.getFromInventory();
        }
        if (other == null) {
            other = known;
        }
        return new Coordinates(other.getLatitude().doubleValue(), other.getLongitude().doubleValue());
    }

    private List<Coordinates> buildVanOriginalStopsForOverhead(Inventory van, Coordinates vanCurrentCoords) {
        // For now we just use current location as the only base stop.
        // You can extend this to include future scheduled stops if you want a richer route.
        return Collections.singletonList(vanCurrentCoords);
    }

    private OffsetDateTime computeNextWorkingTimeForVan(Inventory van, long travelTimeSec) {
        // Start from now + small offset (e.g., 5 minutes) and then clamp into working hours.
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5);
        OffsetDateTime preliminary = now.plusSeconds(travelTimeSec);
        return shiftIntoWorkingHours(preliminary);
    }

    private OffsetDateTime shiftIntoWorkingHours(OffsetDateTime dateTime) {
        // convert to local time (Riyadh, as per your context)
        ZoneId zone = ZoneId.of("Asia/Riyadh");
        ZonedDateTime zdt = dateTime.atZoneSameInstant(zone);

        // push off weekend
        while (zdt.getDayOfWeek() == WEEKEND) {
            zdt = zdt.plusDays(1).withHour(WORK_START.getHour()).withMinute(WORK_START.getMinute());
        }

        LocalTime time = zdt.toLocalTime();
        if (time.isBefore(WORK_START)) {
            zdt = zdt.withHour(WORK_START.getHour()).withMinute(WORK_START.getMinute());
        } else if (time.isAfter(WORK_END)) {
            zdt = zdt.plusDays(1).withHour(WORK_START.getHour()).withMinute(WORK_START.getMinute());
        }

        return zdt.toOffsetDateTime();
    }

    // ----------------- Misc helpers -----------------

    private CandidateGenerationResultDto emptyResult(Order order, OrderItem item) {
        return CandidateGenerationResultDto.builder()
                .orderId(order.getOrderId())
                .orderItemId(item.getOrderItemId())
                .productId(item.getProduct().getProductId())
                .requestedQuantity(item.getQuantity())
                .candidates(Collections.emptyList())
                .build();
    }
}