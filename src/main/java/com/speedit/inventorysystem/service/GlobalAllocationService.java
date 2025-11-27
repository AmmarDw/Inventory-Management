package com.speedit.inventorysystem.service;

import com.speedit.inventorysystem.dto.allocation.*;
import com.speedit.inventorysystem.enums.OrderStatusEnum;
import com.speedit.inventorysystem.enums.MovementStatus;
import com.speedit.inventorysystem.model.*;
import com.speedit.inventorysystem.repository.InventoryStockRepository;
import com.speedit.inventorysystem.repository.OrderRepository;
import com.speedit.inventorysystem.repository.StockMovementRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GlobalAllocationService {

    private final CandidateGeneratorService candidateGeneratorService;
    private final InventoryStockRepository inventoryStockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final OrderRepository orderRepository;

    /**
     * High-level helper: plan AND commit allocation for given orders in one shot.
     * If there is insufficient stock for any OrderItem, it throws an exception
     * and does NOT commit.
     *
     * You can instead call planGlobal(...) and commitGlobalPlan(...) separately
     * if you want to inspect the plan first (for UI control page, etc.).
     */
    @Transactional
    public GlobalAllocationPlan planAndAllocate(List<Order> orders) {
        GlobalAllocationPlan plan = planGlobal(orders);

        if (!plan.isFullyAllocated()) {
            // simple behavior: fail whole operation if not all items can be allocated
            // TODO: support partial allocation / backorders if business rules allow
            throw new IllegalStateException("Not enough stock to fully allocate all order items.");
        }

        commitGlobalPlan(plan);
        return plan;
    }

    /**
     * Phase B - Step 1: Build a global allocation plan by:
     *   - calling Phase A for each OrderItem
     *   - doing a greedy assignment per item while respecting per-InventoryStock limits
     */
    @Transactional
    public GlobalAllocationPlan planGlobal(List<Order> orders) {

        // Collect candidate sets for all order items
        List<CandidateGenerationResultDto> allItemCandidates = new ArrayList<>();

        for (Order order : orders) {
            for (OrderItem item : order.getOrderItems()) {
                CandidateGenerationResultDto result =
                        candidateGeneratorService.generateCandidatesForOrderItem(order, item);
                allItemCandidates.add(result);
            }
        }

        // Map for remaining stock per InventoryStock (available rows).
        // We lazily initialize from the primaryInventoryStock entities we see.
        Map<Integer, Integer> remainingStockById = new HashMap<>();

        // Build order item allocation plans
        List<OrderItemAllocationPlan> itemPlans = new ArrayList<>();

        // Simple ordering: items with fewer candidates first (to help them get stock).
        List<CandidateGenerationResultDto> sortedItems = allItemCandidates.stream()
                .sorted(Comparator.comparingInt(r -> r.getCandidates().size()))
                .collect(Collectors.toList());

        boolean fullyAllocated = true;

        for (CandidateGenerationResultDto itemResult : sortedItems) {
            OrderItem orderItem = findOrderItemFromOrders(orders, itemResult.getOrderItemId());
            int requestedQty = itemResult.getRequestedQuantity();

            OrderItemAllocationPlan itemPlan = OrderItemAllocationPlan.builder()
                    .orderItem(orderItem)
                    .requestedQuantity(requestedQty)
                    .allocatedQuantity(0)
                    .chunks(new ArrayList<>())
                    .build();

            int remainingDemand = requestedQty;

            // Candidates are already sorted by provisionalScore in Phase A
            for (PathCandidateDto candidate : itemResult.getCandidates()) {
                if (remainingDemand <= 0) break;

                InventoryStock primaryStock = candidate.getPrimaryInventoryStock();
                Integer stockId = primaryStock.getInventoryStockId();

                // Initialize remaining stock for this row if first time we see it
                remainingStockById.computeIfAbsent(stockId, id -> primaryStock.getAmount());

                int stockRemaining = remainingStockById.get(stockId);
                if (stockRemaining <= 0) continue;

                int candidateMax = candidate.getMaxFeasibleAmount();
                if (candidateMax <= 0) continue;

                int allocQty = Math.min(remainingDemand, Math.min(candidateMax, stockRemaining));
                if (allocQty <= 0) continue;

                AllocationChunk chunk = AllocationChunk.builder()
                        .orderItem(orderItem)
                        .candidate(candidate)
                        .quantity(allocQty)
                        .build();

                itemPlan.getChunks().add(chunk);
                itemPlan.setAllocatedQuantity(itemPlan.getAllocatedQuantity() + allocQty);

                remainingDemand -= allocQty;
                remainingStockById.put(stockId, stockRemaining - allocQty);
            }

            if (remainingDemand > 0) {
                fullyAllocated = false;
            }

            itemPlans.add(itemPlan);
        }

        GlobalAllocationPlan plan = GlobalAllocationPlan.builder()
                .itemPlans(itemPlans)
                .fullyAllocated(fullyAllocated)
                .build();

        return plan;
    }

    /**
     * Phase B - Step 2: Commit the global plan to the DB.
     * This will:
     *  - decrement available InventoryStock rows
     *  - create/update reserved InventoryStock rows for each OrderItem
     *  - persist StockMovement rows bound to the reserved stocks
     *  - update Order status to ALLOCATED
     */
    @Transactional
    public void commitGlobalPlan(GlobalAllocationPlan plan) {

        // Sanity: if not fully allocated, you might choose to abort here
        // to avoid partial allocations.
        if (!plan.isFullyAllocated()) {
            // TODO: make this configurable (allow partial commit if desired)
            throw new IllegalStateException("Attempting to commit a plan that is not fully allocated.");
        }

        // Track which Orders are involved so we can update their status
        Set<Order> touchedOrders = new HashSet<>();

        for (OrderItemAllocationPlan itemPlan : plan.getItemPlans()) {
            OrderItem item = itemPlan.getOrderItem();
            Order order = item.getOrder();
            touchedOrders.add(order);

            for (AllocationChunk chunk : itemPlan.getChunks()) {
                PathCandidateDto candidate = chunk.getCandidate();
                int quantity = chunk.getQuantity();

                // 1) reload the primary stock from DB to avoid stale state
                InventoryStock primaryStock = candidate.getPrimaryInventoryStock();
                InventoryStock availableStock = inventoryStockRepository.findById(primaryStock.getInventoryStockId())
                        .orElseThrow(() -> new EntityNotFoundException(
                                "InventoryStock not found: " + primaryStock.getInventoryStockId()));

                if (availableStock.getOrderItem() != null) {
                    // somebody reserved it between planning and commit → concurrency issue
                    // Simple handling: fail
                    // TODO: improve concurrency handling (optimistic locking / re-plan)
                    throw new IllegalStateException("Available stock row is no longer available (already reserved).");
                }

                if (availableStock.getAmount() < quantity) {
                    // concurrency drift (stock decreased after planning)
                    // TODO: automatically re-plan instead of failing hard
                    throw new IllegalStateException("Insufficient available stock during commit.");
                }

                // 2) decrement available stock
                availableStock.setAmount(availableStock.getAmount() - quantity);
                inventoryStockRepository.save(availableStock);

                // 3) find or create reserved stock row for this OrderItem + Inventory + Product
                Inventory reservedInventory = availableStock.getInventory();
                Product product = availableStock.getProduct();

                InventoryStock reservedStock = inventoryStockRepository
                        .findReservedByOrderItemAndInventoryAndProduct(item, reservedInventory, product)
                        .orElseGet(() -> {
                            InventoryStock s = new InventoryStock();
                            s.setInventory(reservedInventory);
                            s.setProduct(product);
                            s.setOrderItem(item);
                            s.setAmount(0);
                            s.setEmployee(null);
                            return s;
                        });

                reservedStock.setAmount(reservedStock.getAmount() + quantity);
                reservedStock = inventoryStockRepository.save(reservedStock);

                // 4) persist StockMovement rows for this candidate,
                //    but attach them to the RESERVED stock, not the original available stock
                for (StockMovement movementTemplate : candidate.getMovements()) {

                    StockMovement movement = new StockMovement();
                    movement.setInventoryStock(reservedStock);
                    movement.setFromInventory(movementTemplate.getFromInventory());
                    movement.setToInventory(movementTemplate.getToInventory());
                    movement.setMovementType(movementTemplate.getMovementType());
                    // Ensure we keep it PLANNED for now
                    movement.setMovementStatus(MovementStatus.PLANNED);
                    movement.setMoveAt(movementTemplate.getMoveAt());
                    movement.setEstimatedVolumeCc(movementTemplate.getEstimatedVolumeCc());
                    movement.setAssignedEmployee(movementTemplate.getAssignedEmployee());

                    stockMovementRepository.save(movement);
                }
            }
        }

        // 5) update order status to ALLOCATED
        for (Order order : touchedOrders) {
            order.setOrderStatus(OrderStatusEnum.ALLOCATED);
            orderRepository.save(order);
        }

        // TODO: generate audit logs / notifications if required by your FRs
    }

    // ----------------- helper to map result DTO back to entity -----------------

    private OrderItem findOrderItemFromOrders(List<Order> orders, Integer orderItemId) {
        for (Order o : orders) {
            for (OrderItem item : o.getOrderItems()) {
                if (item.getOrderItemId().equals(orderItemId)) {
                    return item;
                }
            }
        }
        throw new EntityNotFoundException("OrderItem not found in given orders: " + orderItemId);
    }
}

