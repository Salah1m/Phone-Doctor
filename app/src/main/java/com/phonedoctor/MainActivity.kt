package com.phonedoctor

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var capability: TextView
    private lateinit var phoneInfo: TextView

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
            gravity = Gravity.CENTER
            text = getUsbHostCapabilityText()
        }
        status = TextView(this).apply {
            text = "جاهز لفحص اتصال USB"
            textSize = 18f
            setPadding(0, 18, 0, 16)
            setTextColor(0xFF14213D.toInt())
            gravity = Gravity.CENTER
        }

        val scanButton = Button(this).apply {
            text = "فحص أجهزة USB"
            setOnClickListener { scanUsbDevices() }
        }

        details = TextView(this).apply {
            text = "اضغط على الفحص لمعرفة ما إذا كان الهاتف متصلاً بجهاز USB."
            textSize = 15f
            setTextColor(0xFF39465E.toInt())
            setPadding(0, 18, 0, 20)
        }

        val infoTitle = TextView(this).apply {
            text = "معلومات هذا الهاتف"
            textSize = 20f
            setTextColor(0xFF14213D.toInt())
            setPadding(0, 24, 0, 8)
        }
        phoneInfo = TextView(this).apply {
            textDirection = View.TEXT_DIRECTION_RTL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            textSize = 15f
            setTextColor(0xFF39465E.toInt())
            setPadding(0, 8, 0, 8)
        }
        val refreshInfoButton = Button(this).apply {
            text = "تحديث معلومات الهاتف"
            setOnClickListener { showPhoneInfo() }
        }

        val note = TextView(this).apply {
            textDirection = View.TEXT_DIRECTION_RTL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            text = "ملاحظات مهمة:\n" +
                "1. دعم USB Host لا يعني أن كل هاتف آخر سيظهر كجهاز قابل للقراءة.\n" +
                "2. اكتشاف الهاتف لا يثبت سلامة اللوحة الأم.\n" +
                "3. ظهور الجهاز لا يعني أن ملفاته أصبحت متاحة؛ ذلك يعتمد على وضع USB وموافقة الهاتف الآخر.\n" +
                "4. لا يمكن للتطبيق تجاوز قفل الشاشة أو موافقات Android الأمنية."
            textSize = 14f
            setTextColor(0xFF8A3B12.toInt())
            setPadding(0, 24, 0, 0)
        }

        root.addView(title)
        root.addView(capability)
        root.addView(status)
        root.addView(scanButton)
        root.addView(details)
        root.addView(infoTitle)
        root.addView(phoneInfo)
        root.addView(refreshInfoButton)
        root.addView(note)

        setContentView(ScrollView(this).apply { addView(root) })
        showPhoneInfo()
    }

    private fun getUsbHostCapabilityText(): String {
        val supported = packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
        return if (supported) {
            "قدرة USB Host: مدعومة على هذا الهاتف"
        } else {
            "قدرة USB Host: لم يعلن النظام عن دعمها"
        }
    }

    private fun showPhoneInfo() {
        val stat = StatFs(filesDir.absolutePath)
        val totalBytes = stat.totalBytes
        val availableBytes = stat.availableBytes
        val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)

        val battery = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra("level", -1) ?: -1
        val scale = battery?.getIntExtra("scale", -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val temperature = battery?.getIntExtra("temperature", -1) ?: -1
        val chargingStatus = battery?.getIntExtra("status", -1) ?: -1
        val chargingText = when (chargingStatus) {
            2, 5 -> "قيد الشحن أو متصل بالطاقة"
            3 -> "غير مشحونة"
            4 -> "لا يشحن حالياً"
            else -> "غير معروف"
        }
        val tempText = if (temperature >= 0) String.format(Locale.getDefault(), "%.1f °C", temperature / 10f) else "غير متاحة"

        phoneInfo.text =
            "الشركة المصنّعة: ${Build.MANUFACTURER}\n" +
            "العلامة التجارية: ${Build.BRAND}\n" +
            "طراز الجهاز: ${Build.MODEL}\n" +
            "إصدار Android: ${Build.VERSION.RELEASE}\n" +
            "مستوى API: ${Build.VERSION.SDK_INT}\n" +
            "تصحيح أمان Android: ${Build.VERSION.SECURITY_PATCH}\n\n" +
            "مساحة التخزين الداخلية:\n" +
            "الإجمالي: ${formatBytes(totalBytes)}\n" +
            "المستخدم تقريباً: ${formatBytes(usedBytes)}\n" +
            "المتاح: ${formatBytes(availableBytes)}\n\n" +
            "البطارية:\n" +
            "الشحن: ${if (batteryPercent >= 0) "$batteryPercent%" else "غير متاح"}\n" +
            "الحالة: $chargingText\n" +
            "درجة الحرارة: $tempText"
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return String.format(Locale.getDefault(), "%.2f GB", gb)
    }

    private fun scanUsbDevices() {
        val manager = getSystemService(USB_SERVICE) as UsbManager
        val devices = manager.deviceList.values.toList()

        status.text = if (devices.isEmpty()) {
            "لم يتم اكتشاف جهاز USB"
        } else {
            "تم اكتشاف ${devices.size} جهاز"
        }

        details.text = if (devices.isEmpty()) {
            "لم يظهر أي جهاز في قائمة USB Host. عند التجربة، استخدم كابلاً يدعم نقل البيانات، وتأكد من توافق وضع USB بين الهاتفين. قد يحتاج الاتصال المباشر إلى محول أو تغيير دور USB حسب الجهاز."
        } else {
            devices.mapIndexed { index, device ->
                val manufacturer = device.manufacturerName ?: "غير متاح من الجهاز"
                val product = device.productName ?: "غير متاح من الجهاز"
                val interfaces = (0 until device.interfaceCount).map { interfaceIndex ->
                    val usbInterface = device.getInterface(interfaceIndex)
                    "واجهة ${interfaceIndex + 1}: Class ${usbInterface.interfaceClass}, Subclass ${usbInterface.interfaceSubclass}, Protocol ${usbInterface.interfaceProtocol}"
                }.joinToString("\n")

                (index + 1).toString() + ". معلومات الجهاز\n" +
                    "الشركة المصنّعة: " + manufacturer + "\n" +
                    "اسم المنتج: " + product + "\n" +
                    "اسم USB: " + device.deviceName + "\n" +
                    "Vendor ID: " + device.vendorId + "\n" +
                    "Product ID: " + device.productId + "\n" +
                    "فئة الجهاز: " + device.deviceClass + "\n" +
                    "عدد الواجهات: " + device.interfaceCount + "\n" +
                    "إذن الوصول: " + if (manager.hasPermission(device)) "ممنوح" else "غير ممنوح" +
                    (if (interfaces.isNotEmpty()) "\n" + interfaces else "") +
                    "\nإمكانية قراءة الملفات: تحتاج إلى اختبار وضع الاتصال وموافقة الهاتف الآخر."
            }.joinToString("\n\n--------------------\n\n")
        }
    }
}
