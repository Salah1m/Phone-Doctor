package com.phonedoctor

import android.app.Activity
import android.app.ActivityManager
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
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var phoneInfo: LinearLayout
    private lateinit var connectedInfo: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 30, 24, 28)
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(0xFFF2F5FA.toInt())
        }

        val title = TextView(this).apply {
            text = "Phone Doctor"
            textSize = 28f
            setTextColor(0xFF14213D.toInt())
            gravity = Gravity.CENTER
        }
        val capability = TextView(this).apply {
            text = getUsbHostCapabilityText()
            textSize = 15f
            setPadding(0, 16, 0, 8)
            setTextColor(0xFF245B86.toInt())
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            text = "جاهز لفحص اتصال USB"
            textSize = 18f
            setPadding(0, 12, 0, 12)
            setTextColor(0xFF14213D.toInt())
            gravity = Gravity.CENTER
        }
        val scanButton = Button(this).apply {
            text = "فحص أجهزة USB"
            setOnClickListener { scanUsbDevices() }
        }
        details = TextView(this).apply {
            text = "اضغط على الفحص لمعرفة ما إذا كان الهاتف متصلاً بجهاز USB."
            textSize = 14f
            setTextColor(0xFF39465E.toInt())
            textDirection = View.TEXT_DIRECTION_RTL
            setPadding(8, 12, 8, 12)
        }

        val connectedTitle = sectionTitle("معلومات الهاتف المتصل عبر USB")
        connectedInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val infoTitle = sectionTitle("معلومات هذا الهاتف")
        phoneInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
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
            textSize = 13f
            setTextColor(0xFF8A3B12.toInt())
            setPadding(8, 18, 8, 8)
        }

        root.addView(title)
        root.addView(capability)
        root.addView(status)
        root.addView(scanButton)
        root.addView(details)
        root.addView(connectedTitle)
        root.addView(connectedInfo)
        root.addView(infoTitle)
        root.addView(phoneInfo)
        root.addView(refreshInfoButton)
        root.addView(note)

        setContentView(ScrollView(this).apply { addView(root) })
        showPhoneInfo()
        showNoConnectedDevice()
    }

    private fun sectionTitle(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 20f
        setTextColor(0xFF14213D.toInt())
        gravity = Gravity.END
        textDirection = View.TEXT_DIRECTION_RTL
        setPadding(4, 20, 4, 10)
    }

    private fun makeCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(18, 14, 18, 14)
        setBackgroundColor(0xFFFFFFFF.toInt())
        elevation = 2f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 12 }
    }

    private fun addInfoRow(card: LinearLayout, label: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 7, 0, 7)
        }
        val labelView = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(0xFF667085.toInt())
            textDirection = View.TEXT_DIRECTION_RTL
        }
        val valueView = TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(0xFF182230.toInt())
            gravity = Gravity.END
            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
        }
        row.addView(labelView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(valueView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)
    }

    private fun getUsbHostCapabilityText(): String {
        return if (packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
            "USB Host مدعوم على هذا الهاتف"
        } else {
            "هذا الهاتف لم يعلن عن دعم USB Host"
        }
    }

    private fun showPhoneInfo() {
        phoneInfo.removeAllViews()

        val stat = StatFs(filesDir.absolutePath)
        val totalBytes = stat.totalBytes
        val availableBytes = stat.availableBytes
        val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)
        val usedPercent = if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

        val battery = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra("level", -1) ?: -1
        val scale = battery?.getIntExtra("scale", -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val temperature = battery?.getIntExtra("temperature", -1) ?: -1
        val chargingStatus = battery?.getIntExtra("status", -1) ?: -1
        val chargingText = when (chargingStatus) {
            2, 5 -> "قيد الشحن"
            3 -> "غير مشحونة"
            4 -> "لا تشحن حالياً"
            else -> "غير معروف"
        }
        val tempText = if (temperature >= 0) String.format(Locale.getDefault(), "%.1f °C", temperature / 10f) else "غير متاحة"

        val deviceCard = makeCard()
        deviceCard.addView(sectionTitle("📱 الجهاز والنظام"))
        addInfoRow(deviceCard, "الشركة المصنّعة", Build.MANUFACTURER)
        addInfoRow(deviceCard, "العلامة التجارية", Build.BRAND)
        addInfoRow(deviceCard, "طراز الجهاز", Build.MODEL)
        addInfoRow(deviceCard, "إصدار Android", Build.VERSION.RELEASE)
        addInfoRow(deviceCard, "مستوى API", Build.VERSION.SDK_INT.toString())
        addInfoRow(deviceCard, "تصحيح الأمان", Build.VERSION.SECURITY_PATCH)
        phoneInfo.addView(deviceCard)

        val storageCard = makeCard()
        storageCard.addView(sectionTitle("💾 التخزين الداخلي"))
        addInfoRow(storageCard, "الإجمالي", formatBytes(totalBytes))
        addInfoRow(storageCard, "المستخدم تقريباً", formatBytes(usedBytes))
        addInfoRow(storageCard, "المتاح", formatBytes(availableBytes))
        val storageProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = usedPercent
            setPadding(0, 10, 0, 4)
        }
        storageCard.addView(storageProgress)
        val storageCaption = TextView(this).apply {
            text = "المستخدم $usedPercent% من المساحة المحسوبة"
            textSize = 12f
            setTextColor(0xFF667085.toInt())
            gravity = Gravity.END
        }
        storageCard.addView(storageCaption)
        phoneInfo.addView(storageCard)

        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val ramTotal = memoryInfo.totalMem
        val ramAvailable = memoryInfo.availMem
        val ramUsed = (ramTotal - ramAvailable).coerceAtLeast(0L)
        val ramUsedPercent = if (ramTotal > 0L) ((ramUsed * 100L) / ramTotal).toInt().coerceIn(0, 100) else 0

        val ramCard = makeCard()
        ramCard.addView(sectionTitle("🧠 ذاكرة الوصول العشوائي RAM"))
        addInfoRow(ramCard, "الإجمالي", formatBytes(ramTotal))
        addInfoRow(ramCard, "المستخدم تقريباً", formatBytes(ramUsed))
        addInfoRow(ramCard, "المتاح حالياً", formatBytes(ramAvailable))
        ramCard.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = ramUsedPercent
            setPadding(0, 10, 0, 4)
        })
        val ramCaption = TextView(this).apply {
            text = "استخدام الذاكرة حالياً: $ramUsedPercent% — تتغير هذه القيمة حسب تشغيل التطبيقات"
            textSize = 12f
            setTextColor(0xFF667085.toInt())
            gravity = Gravity.END
            textDirection = View.TEXT_DIRECTION_RTL
        }
        ramCard.addView(ramCaption)
        phoneInfo.addView(ramCard)

        val batteryCard = makeCard()
        batteryCard.addView(sectionTitle("🔋 البطارية"))
        addInfoRow(batteryCard, "مستوى الشحن", if (batteryPercent >= 0) "$batteryPercent%" else "غير متاح")
        addInfoRow(batteryCard, "الحالة", chargingText)
        addInfoRow(batteryCard, "درجة الحرارة", tempText)
        if (batteryPercent >= 0) {
            batteryCard.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = batteryPercent
                setPadding(0, 10, 0, 4)
            })
        }
        phoneInfo.addView(batteryCard)
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return String.format(Locale.getDefault(), "%.2f GB", gb)
    }

    private fun showNoConnectedDevice() {
        connectedInfo.removeAllViews()
        val card = makeCard()
        card.addView(sectionTitle("لم يتم فحص هاتف متصل بعد"))
        val message = TextView(this).apply {
            text = "صِل الهاتف الآخر بكابل USB يدعم نقل البيانات، ثم اضغط «فحص أجهزة USB». إذا ظهر كجهاز USB، سنعرض هنا المعلومات التي يسمح الاتصال بقراءتها. قد لا يوفر الهاتف اسمه أو طرازه، ولا يمكن ضمان قراءة البطارية أو التخزين عبر USB العادي."
            textSize = 14f
            textDirection = View.TEXT_DIRECTION_RTL
            setTextColor(0xFF475467.toInt())
            setPadding(4, 8, 4, 8)
        }
        card.addView(message)
        connectedInfo.addView(card)
    }

    private fun scanUsbDevices() {
        val manager = getSystemService(USB_SERVICE) as UsbManager
        val devices = manager.deviceList.values.toList()

        status.text = if (devices.isEmpty()) {
            "لم يتم اكتشاف جهاز USB"
        } else {
            "تم اكتشاف \${devices.size} جهاز USB"
        }
        details.text = if (devices.isEmpty()) {
            "لم يظهر جهاز في قائمة USB Host. تأكد من استخدام كابل يدعم نقل البيانات، وأن الهاتف الآخر يسمح باتصال USB."
        } else {
            "تم رصد أجهزة USB. المعلومات التالية تخص الجهاز المتصل، وليست معلومات هاتفك السليم."
        }

        connectedInfo.removeAllViews()
        if (devices.isEmpty()) {
            showNoConnectedDevice()
            return
        }

        devices.forEachIndexed { index, device ->
            val card = makeCard()
            card.addView(sectionTitle("الجهاز المتصل رقم \${index + 1}"))
            addInfoRow(card, "اسم USB", device.deviceName)
            addInfoRow(card, "الشركة المصنّعة", device.manufacturerName ?: "غير متاحة")
            addInfoRow(card, "اسم المنتج", device.productName ?: "غير متاح")
            addInfoRow(card, "Vendor ID", device.vendorId.toString())
            addInfoRow(card, "Product ID", device.productId.toString())
            addInfoRow(card, "فئة الجهاز", device.deviceClass.toString())
            addInfoRow(card, "عدد الواجهات", device.interfaceCount.toString())
            addInfoRow(card, "إذن USB", if (manager.hasPermission(device)) "ممنوح" else "غير ممنوح")

            val interfaceText = (0 until device.interfaceCount).joinToString("\n") { i ->
                val usbInterface = device.getInterface(i)
                "واجهة \${i + 1}: Class \${usbInterface.interfaceClass}, Subclass \${usbInterface.interfaceSubclass}, Protocol \${usbInterface.interfaceProtocol}"
            }
            if (interfaceText.isNotEmpty()) {
                val interfaces = TextView(this).apply {
                    text = "واجهات الجهاز:\n$interfaceText"
                    textSize = 13f
                    textDirection = View.TEXT_DIRECTION_LTR
                    setTextColor(0xFF475467.toInt())
                    setPadding(4, 8, 4, 8)
                }
                card.addView(interfaces)
            }

            val limitation = TextView(this).apply {
                text = "ملاحظة: هذه بيانات تعريف USB فقط. لا تكشف وحدها طراز الهاتف الكامل أو إصدار Android أو البطارية أو الملفات. تتطلب هذه المعلومات بروتوكولاً مدعوماً وموافقة على الهاتف الآخر."
                textSize = 13f
                textDirection = View.TEXT_DIRECTION_RTL
                setTextColor(0xFF8A3B12.toInt())
                setPadding(4, 12, 4, 4)
            }
            card.addView(limitation)
            connectedInfo.addView(card)
        }
    }
}
