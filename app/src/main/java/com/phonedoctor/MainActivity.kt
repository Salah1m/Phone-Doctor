package com.phonedoctor

import android.app.Activity
import android.app.ActivityManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ImageView
import android.graphics.BitmapFactory
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.phonedoctor.adb.AdbUsbTransport
import com.phonedoctor.adb.AdbIdentity
import com.phonedoctor.adb.AdbHandshake
import com.phonedoctor.adb.AdbExecStream
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var phoneInfo: LinearLayout
    private lateinit var connectedInfo: LinearLayout
    private val usbPermissionAction = "com.phonedoctor.USB_PERMISSION"
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != usbPermissionAction) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            status.text = if (granted) "تم منح إذن USB لهذا التطبيق" else "لم يتم منح إذن USB"
            Toast.makeText(
                this@MainActivity,
                if (granted) "تم منح إذن USB. سنعيد فحص الجهاز." else "لم يتم منح إذن USB.",
                Toast.LENGTH_SHORT
            ).show()
            scanUsbDevices()
        }
    }

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
        val permissionFilter = IntentFilter(usbPermissionAction)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(usbPermissionReceiver, permissionFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(usbPermissionReceiver, permissionFilter)
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(usbPermissionReceiver)
        } catch (_: IllegalArgumentException) {
        }
        super.onDestroy()
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


    private fun usbClassName(value: Int): String = when (value) {
        0 -> "محدد على مستوى الواجهات"
        1 -> "صوت"
        2 -> "اتصالات"
        3 -> "واجهة بشرية HID"
        6 -> "نقل صور PTP (Still Image)"
        8 -> "تخزين USB"
        9 -> "موزّع USB Hub"
        10 -> "بيانات CDC"
        11 -> "بطاقة ذكية"
        14 -> "فيديو"
        224 -> "لاسلكي"
        255 -> "خاص بالشركة المصنّعة"
        else -> "غير معروف"
    }


    private fun requestUsbPermission(manager: UsbManager, device: UsbDevice) {
        val intent = Intent(usbPermissionAction).setPackage(packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val permissionIntent = PendingIntent.getBroadcast(this, device.deviceId, intent, flags)
        manager.requestPermission(device, permissionIntent)
        status.text = "بانتظار موافقة إذن USB على هذا الهاتف"
    }

    private fun scanUsbDevices() {
        val manager = getSystemService(USB_SERVICE) as UsbManager
        val devices = manager.deviceList.values.toList()

        status.text = if (devices.isEmpty()) {
            "لم يتم اكتشاف جهاز USB"
        } else {
            "تم اكتشاف ${devices.size} جهاز USB"
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
            card.addView(sectionTitle("الجهاز المتصل رقم ${index + 1}"))
            addInfoRow(card, "اسم USB", device.deviceName)
            addInfoRow(card, "الشركة المصنّعة", device.manufacturerName ?: "غير متاحة")
            addInfoRow(card, "اسم المنتج", device.productName ?: "غير متاح")
            addInfoRow(card, "Vendor ID", device.vendorId.toString())
            addInfoRow(card, "Product ID", device.productId.toString())
            addInfoRow(card, "فئة الجهاز", "${device.deviceClass} — ${usbClassName(device.deviceClass)}")
            addInfoRow(card, "عدد الواجهات", device.interfaceCount.toString())
            addInfoRow(card, "إذن USB", if (manager.hasPermission(device)) "ممنوح" else "غير ممنوح")

            if (!manager.hasPermission(device)) {
                val permissionButton = Button(this).apply {
                    text = "طلب إذن USB لهذا الجهاز"
                    setOnClickListener { requestUsbPermission(manager, device) }
                }
                card.addView(permissionButton)
                val permissionNote = TextView(this).apply {
                    text = "هذا الإذن يسمح للتطبيق بفحص واجهة USB فقط. لا يفتح قفل الهاتف الآخر ولا يعرض شاشته."
                    textSize = 12f
                    textDirection = View.TEXT_DIRECTION_RTL
                    setTextColor(0xFF8A3B12.toInt())
                    setPadding(4, 4, 4, 8)
                }
                card.addView(permissionNote)
            }

            val interfaceText = (0 until device.interfaceCount).joinToString("\n") { i ->
                val usbInterface = device.getInterface(i)
                "واجهة ${i + 1}: ${usbClassName(usbInterface.interfaceClass)} (Class ${usbInterface.interfaceClass}), Subclass ${usbInterface.interfaceSubclass}, Protocol ${usbInterface.interfaceProtocol}"
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

            val hasPtp = (0 until device.interfaceCount).any {
                device.getInterface(it).interfaceClass == 6
            }
            val hasVideo = (0 until device.interfaceCount).any {
                device.getInterface(it).interfaceClass == 14
            }
            // Standard ADB USB interface descriptor: class 0xFF, subclass 0x42, protocol 0x01.
            val hasAdbInterface = (0 until device.interfaceCount).any {
                val usbInterface = device.getInterface(it)
                usbInterface.interfaceClass == 0xFF &&
                    usbInterface.interfaceSubclass == 0x42 &&
                    usbInterface.interfaceProtocol == 0x01
            }
            addInfoRow(card, "واجهة ADB القياسية", if (hasAdbInterface) "مكتشفة — يلزم اختبار المصادقة" else "غير مكتشفة")

            if (hasAdbInterface) {
                val adbButton = Button(this).apply {
                    text = "اختبار اتصال ADB"
                    isEnabled = true
                    setOnClickListener {
                        if (!manager.hasPermission(device)) {
                            requestUsbPermission(manager, device)
                        } else {
                            isEnabled = false
                            text = "جارٍ اختبار اتصال ADB..."
                            Thread {
                                var transport: AdbUsbTransport? = null
                                var screenshot: ByteArray? = null
                                val result = try {
                                    transport = AdbUsbTransport.open(manager, device)
                                    val handshake = AdbHandshake(transport, AdbIdentity.loadOrCreate())
                                    val auth = handshake.connect()
                                    if (auth.authorized) {
                                        screenshot = AdbExecStream(transport).captureScreenPng()
                                        AdbHandshake.Result(true, "تم الاتصال بنجاح واستلام صورة الشاشة")
                                    } else auth
                                } catch (error: Exception) {
                                    AdbHandshake.Result(false, error.message ?: "تعذر إكمال اتصال ADB")
                                } finally {
                                    try { transport?.close() } catch (_: Exception) { }
                                }
                                val png = screenshot
                                runOnUiThread {
                                    isEnabled = true
                                    text = "إعادة اختبار اتصال ADB"
                                    if (png != null) {
                                        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)
                                        if (bitmap != null) {
                                            val preview = ImageView(this@MainActivity).apply {
                                                setImageBitmap(bitmap)
                                                adjustViewBounds = true
                                                contentDescription = "صورة شاشة الهاتف المتصل"
                                                setPadding(0, 12, 0, 12)
                                            }
                                            card.addView(preview)
                                        }
                                    }
                                    Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()
                                    status.text = result.message
                                }
                            }.start()
                        }
                    }
                }
                card.addView(adbButton)
            }

            val screenAssessment = TextView(this).apply {
                val assessment = when {
                    hasAdbInterface -> "ظهرت واجهة USB تطابق وصف ADB القياسي. هذا لا يعني أن الاتصال مصادق عليه أو أن تصحيح USB مفعّل؛ يجب اختبار المصادقة بمفتاح موثوق."
                    hasVideo -> "ظهرت واجهة USB مصنفة كفيديو. هذا لا يثبت أنها تخرج صورة الشاشة؛ يلزم اختبار بروتوكولها."
                    hasPtp -> "الهاتف يعرض حالياً واجهة PTP لنقل الصور. هذه الواجهة لا تنقل بث الشاشة، ولم تظهر واجهة ADB القياسية أو واجهة فيديو في الفحص."
                    else -> "لم تظهر واجهة ADB القياسية أو واجهة فيديو معروفة في الفحص الحالي. هذا لا يحسم كل طرق الوصول الممكنة."
                }
                text = "تقييم إمكانية الوصول إلى الشاشة:\n$assessment\n\nملاحظة ADB: لا يمكن استعماله إلا إذا كان تصحيح USB مفعّلاً على الهاتف المستهدف وقَبِل مفتاح هذا الجهاز مسبقاً.\nملاحظة إخراج الفيديو: واجهات USB العادية لا تكشف بشكل موثوق دعم DisplayPort Alt Mode؛ يجب التحقق من مواصفات الطراز أو اختباره بمعدات مناسبة.\nإذن USB الممنوح هنا لا يفتح قفل الهاتف ولا يتيح بث الشاشة تلقائياً."
                textSize = 13f
                textDirection = View.TEXT_DIRECTION_RTL
                setTextColor(0xFF245B86.toInt())
                setPadding(4, 12, 4, 8)
            }
            card.addView(screenAssessment)

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
