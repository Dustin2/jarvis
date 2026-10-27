package dev.dux.organizer.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        AppDatabase::class.java,
    ).build()
    private val shopping = ShoppingRepository(db.shopping())

    @After fun tearDown() = db.close()

    private suspend fun firstListId() = shopping.lists.first().first().id
    private suspend fun items(listId: String) = shopping.items(listId).first()

    @Test fun seedsSuperOnlyOnce() = runBlocking {
        shopping.seedIfEmpty()
        shopping.seedIfEmpty()
        assertEquals(listOf(ShoppingRepository.INITIAL_LIST), shopping.lists.first().map { it.name })
    }

    @Test fun addsInOrderWithOptionalQuantityAndRejectsInvalid() = runBlocking {
        shopping.seedIfEmpty()
        val id = firstListId()
        assertTrue(shopping.addItem(id, " Leche "))
        assertTrue(shopping.addItem(id, "Tortillas", " 2 kg "))
        assertFalse(shopping.addItem(id, "   "))
        assertFalse(shopping.addItem(id, "Arroz", "x".repeat(ShoppingRepository.MAX_QUANTITY_LENGTH + 1)))
        val list = items(id)
        assertEquals(listOf("Leche", "Tortillas"), list.map { it.name })
        assertNull(list[0].quantity)
        assertEquals("2 kg", list[1].quantity)
    }

    @Test fun checkRemoveCheckedAndUncheckAll() = runBlocking {
        shopping.seedIfEmpty()
        val id = firstListId()
        listOf("Leche", "Pan", "Huevo").forEach { shopping.addItem(id, it) }
        val (leche, pan) = items(id)
        shopping.setChecked(leche.id, true)
        shopping.setChecked(pan.id, true)
        shopping.setChecked(pan.id, false)
        assertEquals(listOf("Leche"), items(id).filter { it.checked }.map { it.name })

        shopping.uncheckAll(id)
        assertTrue(items(id).none { it.checked })

        shopping.setChecked(leche.id, true)
        shopping.removeChecked(id)
        assertEquals(listOf("Pan", "Huevo"), items(id).map { it.name })
    }

    @Test fun deletingAListDeletesItsItemsButNeverTheLastList() = runBlocking {
        shopping.seedIfEmpty()
        val superId = firstListId()
        val hardware = shopping.createList("Ferretería")!!
        shopping.addItem(hardware, "Focos LED")
        shopping.addItem(superId, "Leche")

        shopping.deleteList(hardware)
        assertEquals(listOf(superId), shopping.lists.first().map { it.id })
        assertEquals(listOf("Leche"), db.shopping().getAllItems().map { it.name })

        shopping.deleteList(superId)
        assertEquals(listOf(superId), shopping.lists.first().map { it.id })
    }

    @Test fun renameAndCreateValidateTheName() = runBlocking {
        shopping.seedIfEmpty()
        val id = firstListId()
        assertTrue(shopping.renameList(id, " Despensa "))
        assertFalse(shopping.renameList(id, " "))
        assertNull(shopping.createList(""))
        assertEquals(listOf("Despensa"), shopping.lists.first().map { it.name })
    }
}
