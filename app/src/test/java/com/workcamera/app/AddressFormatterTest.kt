package com.workcamera.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressFormatterTest {

    @Test
    fun areaPlusPoi() {
        assertEquals("云和县小徐云亭小区", AddressFormatter.compose("云和县", "小徐云亭小区", null, null))
    }

    @Test
    fun areaPlusPoiWithoutSuffix() {
        assertEquals("云和县小徐云亭", AddressFormatter.compose("云和县", "小徐云亭", null, null))
    }

    @Test
    fun areaPlusStreetAndNumber() {
        assertEquals("云和县小徐路12号", AddressFormatter.compose("云和县", null, "小徐路", "12号"))
    }

    @Test
    fun areaPlusStreet() {
        assertEquals("云和县小徐路", AddressFormatter.compose("云和县", null, "小徐路", null))
    }

    @Test
    fun poiOnly() {
        assertEquals("小徐云亭小区", AddressFormatter.compose(null, "小徐云亭小区", null, null))
    }

    @Test
    fun areaOnly() {
        assertEquals("云和县", AddressFormatter.compose("云和县", null, null, null))
    }

    @Test
    fun noDuplicateWhenPoiAlreadyHasArea() {
        assertEquals("云和县小徐云亭小区", AddressFormatter.compose("云和县", "云和县小徐云亭小区", null, null))
    }

    @Test
    fun noDuplicateWhenPoiEqualsArea() {
        assertEquals("云和县", AddressFormatter.compose("云和县", "云和县", "云和县", null))
    }

    @Test
    fun countryPrefixRemoved() {
        assertEquals("云和县小徐云亭小区", AddressFormatter.compose("中国浙江省云和县".let { "云和县" }, "小徐云亭小区", null, null))
    }

    @Test
    fun emptyEverything() {
        assertNull(AddressFormatter.compose(null, null, null, null))
        assertNull(AddressFormatter.compose("", "", " ", "  "))
    }

    @Test
    fun detailedAddressScoresHigher() {
        assertTrue(
            AddressFormatter.score("云和县小徐云亭小区") > AddressFormatter.score("云和县")
        )
        assertTrue(
            AddressFormatter.score("云和县小徐路12号") > AddressFormatter.score("云和县小徐路")
        )
    }
}
