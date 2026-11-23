package com.phonedoctor

import android.app.Activity
import android.content.pm.PackageManager
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
    private lateinit var capability: TextView

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
        capability = TextView(this).apply {
            textSize = 16f
            setPadding(0, 24, 0, 8)
            setTextColor(0xFF14213D.toInt())
            text = getUsbHostCapabilityText()
        }
        status = TextView(this).apply {
            text = "جاهز لفحص اتصال USB"
            textSize = 18f
            setPadding(0, 18, 0, 16)
            setTextColor(0xFF14213D.toInt())
        }
        details = TextView(this).apply {
            text = "عند توفر الكابل، صِل الهاتف الآخر بهذا الهاتف ثم اضغط على فحص الاتصال."
            textSize = 15f
            setTextColor(0xFF39465E.toInt())
            setPadding(0, 8, 0, 20)
        }

        val scanButton = Button(this).apply {
            text = "فحص أجهزة USB"
            setOnClickListener { scanUsbDevices() }
        }

        val note = TextView(this).apply {
            text = "\nملاحظات مهمة:\n1. دعم USB Host لا يعني أن كل هاتف آخر سيظهر كجهاز قابل للقراءة.\n2. اكتشاف الهاتف لا يثبت سلامة اللوحة الأم.\n3. عرض الشاشة والتحكم يتطلبان دعماً وصلاحيات مناسبة على الهاتف المستهدف؛ لا يمكن للتطبيق تجاوز قفل الشاشة أو موافقات Android الأمنية."
            textSize = 14f
            setTextColor(0xFF8A3B12.toInt())
            setPadding(0, 24, 0, 0)
        }

        root.addView(title)
        root.addView(capability)
        root.addView(status)
        root.addView(scanButton)
        root.addView(details)
        root.addView(note)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun getUsbHostCapabilityText(): String {
        val supported = packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
        return if (supported) {
            "قدرة USB Host: مدعومة على هذا الهاتف"
        } else {
            "قدرة USB Host: لم يعلن النظام عن دعمها"
        }
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
            "لم يظهر أي جهاز في قائمة USB Host. عند التجربة، تأكد من كابل يدعم نقل البيانات، ومن توافق وضع USB بين الهاتفين. قد يحتاج الاتصال المباشر بين هاتفين إلى محول أو تغيير دور USB، حسب الجهاز."
        } else {
            devices.mapIndexed { index, device ->
                (index + 1).toString() + ". " + device.deviceName + "\n" +
                    "Vendor ID: " + device.vendorId + "\n" +
                    "Product ID: " + device.productId + "\n" +
                    "Interfaces: " + device.interfaceCount + "\n" +
                    "صلاحية الوصول: " + if (manager.hasPermission(device)) "ممنوحة" else "غير ممنوحة"
            }.joinToString("\n\n")
        }
    }
}
