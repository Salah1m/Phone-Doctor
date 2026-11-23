package com.phonedoctor

import android.app.Activity
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var details: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 28)
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(0xFFF4F6FA.toInt())
        }

        val title = TextView(this).apply {
            text = "Phone Doctor"
            textSize = 28f
            setTextColor(0xFF14213D.toInt())
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            text = "جاهز لفحص اتصال USB"
            textSize = 18f
            setPadding(0, 28, 0, 16)
            setTextColor(0xFF14213D.toInt())
        }
        details = TextView(this).apply {
            text = "وصّل الهاتف الآخر بكابل USB مناسب وOTG، ثم اضغط على فحص الاتصال."
            textSize = 15f
            setTextColor(0xFF39465E.toInt())
            setPadding(0, 8, 0, 20)
        }

        val scanButton = Button(this).apply {
            text = "فحص أجهزة USB"
            setOnClickListener { scanUsbDevices() }
        }

        val note = TextView(this).apply {
            text = "\nملاحظة مهمة:\nاكتشاف الهاتف عبر USB لا يعني أن الشاشة يمكن عرضها أو أن القفل يمكن تجاوزه. يتطلب عرض الشاشة والتحكم دعماً وصلاحيات من الهاتف المستهدف."
            textSize = 14f
            setTextColor(0xFF8A3B12.toInt())
            setPadding(0, 24, 0, 0)
        }

        root.addView(title)
        root.addView(status)
        root.addView(scanButton)
        root.addView(details)
        root.addView(note)

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    private fun scanUsbDevices() {
        val manager = getSystemService(USB_SERVICE) as UsbManager
        val devices = manager.deviceList.values.toList()

        status.text = if (devices.isEmpty()) {
            "لم يتم اكتشاف جهاز USB"
        } else {
            "تم اكتشاف " + devices.size + " جهاز"
        }

        details.text = if (devices.isEmpty()) {
            "تأكد من استخدام كابل يدعم نقل البيانات، ومن أن هاتفك السليم يدعم USB host/OTG. بعض الكابلات مخصصة للشحن فقط."
        } else {
            devices.mapIndexed { index, device ->
                (index + 1).toString() + ". " + device.deviceName + "\n" +
                    "Vendor ID: " + device.vendorId + "\n" +
                    "Product ID: " + device.productId + "\n" +
                    "Interfaces: " + device.interfaceCount
            }.joinToString("\n\n")
        }
    }
}
