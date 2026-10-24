package com.goldquest.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Valores esperados gerados rodando o pricing.py original (Python) nas mesmas entradas. */
class PricingTest {
    private val hoje = LocalDate.of(2026, 10, 24)

    private fun preco(base: Int, compras: List<LocalDate>, criadoHa: Long) =
        calcularPreco(base, compras, hoje.minusDays(criadoHa), hoje)

    @Test
    fun itemNovoTemPrecoNormal() {
        val info = preco(100, emptyList(), 0)
        assertEquals(100, info.price)
        assertEquals(Trend.BASE, info.trend)
    }

    @Test
    fun itemNuncaCompradoEntraEmPromocao() {
        assertEquals(80, preco(100, emptyList(), 8).price)
        assertEquals(70, preco(100, emptyList(), 30).price)  // desconto máximo de 30%
        assertEquals("Promoção: nunca comprado", preco(100, emptyList(), 8).reason)
    }

    @Test
    fun comprasRecentesEncarecem() {
        val um = preco(100, listOf(hoje), 30)
        assertEquals(110, um.price)
        assertEquals("Comprado 1 vez nos últimos 10 dias", um.reason)

        val tres = preco(100, listOf(hoje, hoje.minusDays(1), hoje.minusDays(3)), 30)
        assertEquals(125, tres.price)
        assertEquals("Comprado 3 vezes nos últimos 10 dias", tres.reason)
    }

    @Test
    fun precoRespeitaTetoEPiso() {
        assertEquals(180, preco(100, List(10) { hoje }, 30).price)
        assertEquals(5, preco(10, emptyList(), 60).price)  // nunca abaixo de 5
    }

    @Test
    fun itemEsquecidoEntraEmPromocao() {
        val sete = preco(50, listOf(hoje.minusDays(7)), 30)
        assertEquals(45, sete.price)
        assertEquals("Promoção: 7 dias sem usar", sete.reason)
        assertEquals(210, preco(300, listOf(hoje.minusDays(20)), 60).price)
    }
}
