package com.elenglish.studymentor.domain.model

data class ShopItem(
    val id: String,
    val name: String,
    val description: String?,
    val priceShells: Int,
    val available: Boolean,
    val owned: Boolean,
)

data class InventoryItem(
    val itemId: String,
    val quantity: Int,
    val equipped: Boolean,
)

data class EconomyProjection(
    val currency: String,
    val balance: Int,
    val shopItems: List<ShopItem>,
    val inventory: List<InventoryItem>,
)

data class PendingPurchase(
    val idempotencyKey: String,
    val itemId: String,
)

data class PurchaseResult(
    val purchaseId: String,
    val itemId: String,
    val priceShells: Int,
    val balance: Int,
    val inventoryQuantity: Int,
    val purchasedAt: String,
    val wasReplay: Boolean,
)
