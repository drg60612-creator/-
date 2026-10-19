package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Member
import com.example.data.model.ServiceLocation
import com.example.data.model.ShiftPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("إدارة الإسناد الطبي", appName)
    }

    @Test
    fun `test member model mapping`() {
        val member = Member(
            id = "mem1",
            name = "د. أحمد",
            role = "مشرف",
            defaultShiftDay = "السبت",
            badge = "بيضاء"
        )
        val map = member.toMap()
        assertEquals("د. أحمد", map["name"])
        assertEquals("مشرف", map["role"])
        assertEquals("السبت", map["defaultShiftDay"])
    }

    @Test
    fun `test service location and period models`() {
        val loc = ServiceLocation(id = "loc1", name = "CPR", active = true)
        val per = ShiftPeriod(id = "per1", name = "صباحية", active = true)
        assertTrue(loc.active)
        assertEquals("CPR", loc.name)
        assertEquals("صباحية", per.name)
    }
}
