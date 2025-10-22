// js/stock-inventory-view.js
// Specific logic for the /monitor-stock/inventory/{id} page

document.addEventListener('DOMContentLoaded', function() {
    // --- STATE ---
    const data = JSON.parse(stockDataJson);
    let currentViewBy = initialViewBy;
    
    // --- DOM REFERENCES ---
    const level2Container = document.getElementById('level-2-container');
    const level3Container = document.getElementById('level-3-container');
    const viewByRadios = document.querySelectorAll('input[name="viewBy"]');

    // --- HELPER MAPS for quick lookups ---
    const productsMap = new Map(data.products.map(p => [p.productId, p]));
    const usersMap = new Map(data.users.map(u => [u.userId, u]));
    const ordersMap = new Map(data.orders.map(o => [o.orderId, o]));
    const orderItemsMap = new Map(data['order-items'].map(oi => [oi.orderItemId, oi]));

    // --- INITIALIZATION ---
    render();
    setupEventListeners();

    // --- EVENT LISTENERS ---
    function setupEventListeners() {
        // Listener for view-by radio buttons
        viewByRadios.forEach(radio => {
            radio.addEventListener('change', (e) => {
                currentViewBy = e.target.value;
                render();
            });
        });

        // ✨ NEW & CORRECTED: Listener for clicks on Level 2 items
        level2Container.addEventListener('click', (e) => {
            const row = e.target.closest('.level-2-row');
            if (!row) return;

            row.classList.toggle('active-row');
            filterLevel3Content(); // This will now work correctly
        });
    }

    // --- "Jira-style" FILTERING LOGIC ---
    /**
     * 🔄 CORRECTED: Filters the visibility of items AND their parent groups in the
     * Level 3 panel based on which rows are selected in the Level 2 panel.
     */
    function filterLevel3Content() {
        const level2Panel = document.getElementById('level-2-container');
        const level3Panel = document.getElementById('level-3-container');
        if (!level2Panel || !level3Panel) return;

        // Get all currently active rows from the 2nd level panel
        const activeRows = level2Panel.querySelectorAll('.level-2-row.active-row');
        const activeLevel2Ids = new Set(Array.from(activeRows).map(row => row.dataset.level2Id));

        // --- Step 1: Filter the individual rows (the <tr> elements) ---
        const allLevel3Items = level3Panel.querySelectorAll('.level-3-item');
        const isFilterActive = activeLevel2Ids.size > 0;

        allLevel3Items.forEach(item => {
            if (!isFilterActive) {
                // If no filter is active, ensure all rows are visible
                item.style.display = ''; 
            } else {
                // If filtering is active, show/hide rows based on the selection
                const itemLevel2Id = item.dataset.level2Id;
                item.style.display = activeLevel2Ids.has(itemLevel2Id) ? '' : 'none';
            }
        });

        // --- Step 2: Filter the parent groups (the <div> containers for each table) ---
        const allLevel3Groups = level3Panel.querySelectorAll('.level-3-group');
        
        allLevel3Groups.forEach(group => {
            if (!isFilterActive) {
                // If no filter is active, ensure all groups are visible
                group.style.display = '';
            } else {
                // Check if this group contains any visible rows
                const visibleItemInGroup = group.querySelector('.level-3-item:not([style*="display: none"])');
                
                // If there are no visible items left in this group, hide the whole group.
                // Otherwise, make sure it's visible.
                group.style.display = visibleItemInGroup ? '' : 'none';
            }
        });
    }

    // --- MAIN RENDER FUNCTION ---
    function render() {
        if (currentViewBy === 'product') {
            renderViewByProduct();
        } else {
            renderViewByOrder();
        }
        // After rendering, ensure the filter state is applied
        filterLevel3Content();
    }

    // --- RENDER LOGIC: VIEW BY PRODUCT ---
    function renderViewByProduct() {
        const productsAggregated = aggregateProducts();
        let level2Html = '<div class="management-table"><table>';
        level2Html += `<thead><tr><th>ID</th><th>Information</th><th>Reserved</th><th>Available</th><th>Total</th></tr></thead><tbody>`;
        productsAggregated.forEach(p => {
            level2Html += `
            <tr class="level-2-row" data-level2-id="${p.productId}">
                <td>${p.productId}</td>
                <td style="text-align: left;">${formatContainer(p.productInfo)}</td>
                <td>${p.reserved}</td>
                <td>${p.available}</td>
                <td>${p.total}</td>
            </tr>
        `;});
        level2Html += '</tbody></table></div>';
        level2Container.innerHTML = level2Html;

        const { availableStock, reservedByOrder } = groupStockByOrder();
        let level3Html = '';
        if (availableStock.length > 0) {
            level3Html += '<div class="level-3-group">';
            level3Html += '<div class="level-3-header available-header">Available Products</div>';
            level3Html += '<div class="management-table"><table><thead><tr><th>Stock ID</th><th>Product Info</th><th>Amount</th><th>Employee</th></tr></thead><tbody>';
            availableStock.forEach(stock => {
                const product = productsMap.get(stock.productId);
                const employee = stock.employeeId ? usersMap.get(stock.employeeId) : null;
                level3Html += `
                    <tr class="level-3-item" data-level2-id="${stock.productId}">
                        <td>${stock.inventoryStockId}</td>
                        <td data-tooltip="${formatTooltip(product.productInfo)}">
                            <span class="truncated-text">${product.productInfo}</span>
                        </td>
                        <td>${stock.amount}</td>
                        <td>${employee ? employee.name : 'N/A'}</td>
                    </tr>
                `;
            });
            level3Html += '</tbody></table></div></div>';
        }

        Object.entries(reservedByOrder).forEach(([orderId, stocks]) => {
            const order = ordersMap.get(parseInt(orderId));
            const client = usersMap.get(order.clientId);
            const supervisor = usersMap.get(order.supervisorId);
            level3Html += `<div class="level-3-group">`;
            level3Html += `<div class="level-3-header" data-tooltip="Delivery: ${order.deliveryLocation}">
                <span>ID: #${order.orderId}</span><span>Status: ${order.status}</span><span>Client: ${client.name}</span><span>Supervisor: ${supervisor.name}</span>
            </div>`;
            level3Html += '<div class="management-table"><table><thead><tr><th>Stock ID</th><th>Product Info</th><th>Amount</th><th>Employee</th><th>Discount</th><th>Item ID</th></tr></thead><tbody>';
            stocks.forEach(stock => {
                const product = productsMap.get(stock.productId);
                const orderItem = orderItemsMap.get(stock.orderItemId);
                const employee = stock.employeeId ? usersMap.get(stock.employeeId) : null;
                level3Html += `
                    <tr class="level-3-item" data-level2-id="${stock.productId}">
                        <td>${stock.inventoryStockId}</td>
                        <td data-tooltip="${formatTooltip(product.productInfo)}">
                            <span class="truncated-text">${product.productInfo}</span>
                        </td>
                        <td>${stock.amount} / ${orderItem.quantity}</td>
                        <td>${employee ? employee.name : 'N/A'}</td>
                        <td>${orderItem.discount}</td>
                        <td>${stock.orderItemId}</td>
                    </tr>
                `;
            });
            level3Html += '</tbody></table></div></div>';
        });
        level3Container.innerHTML = level3Html;
    }

    // --- RENDER LOGIC: VIEW BY ORDER ---
    function renderViewByOrder() {
        const ordersAggregated = aggregateOrders();
        let level2Html = '<div class="management-table"><table>';
        level2Html += `<thead><tr><th>ID</th><th>Status</th><th>Client</th><th>Supervisor</th><th data-tooltip="Existed vs. Total">Products</th><th data-tooltip="Existed vs. Total">Goods</th></tr></thead><tbody>`;
        level2Html += `<tr class="level-2-row" data-level2-id="available">
            <td>Available</td><td>-</td><td>-</td><td>-</td>
            <td>${ordersAggregated.available.productTypes} Total</td>
            <td>${ordersAggregated.available.totalGoods} Total</td>
        </tr>`;
        ordersAggregated.orders.forEach(o => {
            const client = usersMap.get(o.clientId);
            const supervisor = usersMap.get(o.supervisorId);
            level2Html += `<tr class="level-2-row" data-level2-id="${o.orderId}">
                <td>#${o.orderId}</td><td>${o.status}</td><td>${client.name}</td><td>${supervisor.name}</td>
                <td data-tooltip="Existed vs. Total">${o.productTypes.existing} / ${o.productTypes.total}</td>
                <td data-tooltip="Existed vs. Total">${o.goods.existing} / ${o.goods.total}</td>
            </tr>`;
        });
        level2Html += '</tbody></table></div>';
        level2Container.innerHTML = level2Html;

        const stockByProduct = groupStockByProduct();
        let level3Html = '';
        Object.entries(stockByProduct).forEach(([productId, stocks]) => {
            const product = productsMap.get(parseInt(productId));
            const totalStockInInv = stocks.reduce((sum, s) => sum + s.amount, 0);
            level3Html += `<div class="level-3-group">`;
            level3Html += `
                <div class="level-3-header">
                    <span>ID: #${product.productId}</span>
                    <span data-tooltip="${formatTooltip(product.productInfo)}"><span class="truncated-text">${product.productInfo}</span></span>
                    <span>Price: $${product.price}</span>
                    <span>Total Stock: ${totalStockInInv}</span>
                </div>
            `;
            level3Html += '<div class="management-table"><table><thead><tr><th>Stock ID</th><th>Amount</th><th>Employee</th><th>Order ID</th></tr></thead><tbody>';
            stocks.sort((a, b) => (a.orderItemId === null ? -1 : 1) - (b.orderItemId === null ? -1 : 1));
            stocks.forEach(stock => {
                const employee = stock.employeeId ? usersMap.get(stock.employeeId) : null;
                const orderId = stock.orderItemId ? orderItemsMap.get(stock.orderItemId).orderId : null;
                level3Html += `<tr class="level-3-item" data-level2-id="${orderId || 'available'}">
                    <td>${stock.inventoryStockId}</td><td>${stock.amount}</td><td>${employee ? employee.name : 'N/A'}</td>
                    <td>${orderId ? `#${orderId}` : '<span style="color: green; font-weight: 500;">Available</span>'}</td>
                </tr>`;
            });
            level3Html += '</tbody></table></div></div>';
        });
        level3Container.innerHTML = level3Html;
    }

    // --- AGGREGATION & GROUPING LOGIC ---
    function aggregateProducts() {
        const productData = {};
        data['inventory-stocks'].forEach(stock => {
            const pId = stock.productId;
            if (!productData[pId]) {
                const product = productsMap.get(pId);
                productData[pId] = { productId: pId, productInfo: product.productInfo, reserved: 0, available: 0, total: 0 };
            }
            if (stock.orderItemId === null) {
                productData[pId].available += stock.amount;
            } else {
                productData[pId].reserved += stock.amount;
            }
            productData[pId].total += stock.amount;
        });
        return Object.values(productData).sort((a,b) => a.productId - b.productId);
    }

    function groupStockByOrder() {
        const availableStock = [];
        const reservedByOrder = {};
        data['inventory-stocks'].forEach(stock => {
            if (stock.orderItemId === null) {
                availableStock.push(stock);
            } else {
                const orderId = orderItemsMap.get(stock.orderItemId).orderId;
                if (!reservedByOrder[orderId]) {
                    reservedByOrder[orderId] = [];
                }
                reservedByOrder[orderId].push(stock);
            }
        });
        return { availableStock, reservedByOrder };
    }

    function aggregateOrders() {
        const availableStocks = data['inventory-stocks'].filter(s => s.orderItemId === null);
        const availableProductTypes = new Set(availableStocks.map(s => s.productId));
        const available = {
            productTypes: availableProductTypes.size,
            totalGoods: availableStocks.reduce((sum, s) => sum + s.amount, 0)
        };
        
        const ordersList = Array.from(ordersMap.values()).map(order => {
            const orderItemsForThisOrder = data['order-items'].filter(oi => oi.orderId === order.orderId);
            const orderItemIds = new Set(orderItemsForThisOrder.map(oi => oi.orderItemId));
            
            const stocksForThisOrder = data['inventory-stocks'].filter(s => s.orderItemId !== null && orderItemIds.has(s.orderItemId));
            const existingProductTypes = new Set(stocksForThisOrder.map(s => s.productId));

            return {
                orderId: order.orderId, status: order.status, clientId: order.clientId, supervisorId: order.supervisorId,
                productTypes: { existing: existingProductTypes.size, total: order.totalOrderItems },
                goods: { existing: stocksForThisOrder.reduce((sum, s) => sum + s.amount, 0), total: order.totalOrderItemsQuantities }
            };
        });

        return { available, orders: ordersList.sort((a,b) => a.orderId - b.orderId) };
    }

    function groupStockByProduct() {
        const stockByProduct = {};
        data['inventory-stocks'].forEach(stock => {
            const pId = stock.productId;
            if (!stockByProduct[pId]) {
                stockByProduct[pId] = [];
            }
            stockByProduct[pId].push(stock);
        });
        return stockByProduct;
    }

    
    // --- UTILITY FUNCTIONS ---
    function formatTooltip(info) {
        return info.replace(/ -> /g, '\n↳ ');
    }

    /**
     * Prepends a package icon to a product's info string if it's a container.
     * @param {string} productInfo - The product's descriptive string.
     * @returns {string} The product info string, with a prepended SVG icon if it's a container.
     */
    function formatContainer(productInfo) {
        // A simple way to identify a container is by checking for the hierarchy arrow.
        const isContainer = productInfo.includes(' -> ');

        if (isContainer) {
            // This is a small, inline SVG for the "package" icon, styled to fit in the table.
            const iconSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" data-lucide="corner-right-down" class="lucide lucide-corner-right-down corner-right-down-icon" style="padding-left: 5px;"><path d="m10 15 5 5 5-5"></path><path d="M4 4h7a4 4 0 0 1 4 4v12"></path></svg>`;
            return productInfo.replace(/ -> /g, iconSvg + '<br><div style="margin: 5px;"></div>');
        }

        // If it's not a container, return the original string.
        return productInfo;
    }
});