package com.example.leituradiariadabiblia

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.Manifest
import android.view.Gravity
import android.view.KeyEvent
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.text.TextUtils
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.text.InputType
import android.text.InputFilter
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.TimePicker
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val prefsName = "leitura_biblia_prefs"
    private val completedPrefix = "completed_"
    private val plannedPrefix = "planned_"
    private val plannedTimePrefix = "planned_time_"
    private val completedTimePrefix = "completed_time_"
    private val selectedIds = linkedSetOf<String>()
    private lateinit var root: LinearLayout
    private var currentReading: BibleReading? = null
    private var chronologicalMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chronologicalMode = prefs().getBoolean("chronological_mode", false)
        showHome()
    }

    private val systemBackCallback = OnBackInvokedCallback {
        if (currentReading != null) {
            showHome()
        } else {
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        // Android 13+ usa o dispatcher de voltar; registrar o callback evita
        // que o gesto/botão do sistema ignore onBackPressed() em versões recentes.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                systemBackCallback
            )
        }
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(systemBackCallback)
        }
        super.onStop()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (currentReading != null) showHome() else super.onBackPressed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && currentReading != null) {
            showHome()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun latestCompletedId(completed: Map<String, String>): String? {
        if (completed.isEmpty()) return null

        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).apply {
            isLenient = false
        }

        return completed.keys.maxByOrNull { id ->
            val date = completed[id].orEmpty()
            val time = completedTime(id) ?: "00:00"
            try {
                formatter.parse("$date $time")?.time ?: Long.MIN_VALUE
            } catch (_: Exception) {
                Long.MIN_VALUE
            }
        }
    }

    private fun today(): String =
        SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date())

    private fun nowTime(): String =
        SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date())

    private fun prefs() = getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private fun completedMap(): MutableMap<String, String> =
        prefs().all.filterKeys { it.startsWith(completedPrefix) }
            .mapKeys { it.key.removePrefix(completedPrefix) }
            .mapValues { it.value as? String ?: "" }.toMutableMap()

    private fun plannedDate(id: String): String? = prefs().getString(plannedPrefix + id, null)
    private fun plannedTime(id: String): String? = prefs().getString(plannedTimePrefix + id, null)
    private fun completedTime(id: String): String? = prefs().getString(completedTimePrefix + id, null)

    private fun saveCompleted(values: Map<String, String>) {
        val editor = prefs().edit()
        prefs().all.keys.filter { it.startsWith(completedPrefix) }.forEach { editor.remove(it) }
        values.forEach { (id, date) -> editor.putString(completedPrefix + id, date) }
        editor.apply()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun roundedBackground(color: Int, radiusDp: Int = 10): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun text(
        value: String,
        size: Float = 14f,
        bold: Boolean = false,
        color: Int = Color.rgb(30, 30, 30)
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(3), dp(3), dp(3), dp(3))
    }

    private fun button(value: String, action: () -> Unit) =
        Button(this).apply {
            text = value
            isAllCaps = false
            textSize = 12f
            setPadding(dp(5), 0, dp(5), 0)
            minHeight = dp(36)
            setOnClickListener { action() }
        }

    private fun sectionColor(section: String): Int {
        val s = section.uppercase(Locale.ROOT)
        return when {
            "ESCRITOS DE MOISÉS" in s -> Color.rgb(237, 241, 204) // verde-claro do PDF
            "TERRA PROMETIDA" in s -> Color.rgb(207, 224, 242) // azul
            "REIS GOVERNAVAM" in s -> Color.rgb(250, 226, 183) // laranja claro
            "RETORNAM" in s -> Color.rgb(224, 233, 237) // cinza azulado
            "CÂNTICOS" in s || "SABEDORIA" in s -> Color.rgb(207, 229, 241) // azul claro
            "PROFETAS" in s -> Color.rgb(250, 216, 200) // pêssego
            "VIDA E DO MINISTÉRIO" in s -> Color.rgb(255, 230, 142) // amarelo
            "CONGREGAÇÃO CRISTÃ" in s -> Color.rgb(205, 230, 242) // azul
            "CARTAS DE PAULO" in s -> Color.rgb(247, 220, 224) // rosa
            "OUTROS APÓSTOLOS" in s -> Color.rgb(237, 218, 216) // rosa/bege
            else -> Color.rgb(238, 238, 238)
        }
    }

    private fun baseColumn(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(255, 255, 255))
        }

    private fun showReminderDialog() {
        val reminderPrefs = getSharedPreferences("lembrete_biblia", Context.MODE_PRIVATE)
        val enabled = reminderPrefs.getBoolean("enabled", false)
        val savedHour = reminderPrefs.getInt("hour", 20)
        val savedMinute = reminderPrefs.getInt("minute", 0)
        val accent = Color.rgb(75, 91, 62)       // verde oliva escuro
        val borderColor = Color.rgb(126, 137, 111)
        val textColor = Color.rgb(40, 43, 36)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        layout.addView(
            text("Escolha o horário para receber uma notificação todos os dias.", 15f),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) }
        )

        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun timeField(value: Int, hint: String): EditText = EditText(this).apply {
            setText(String.format(Locale.US, "%02d", value))
            this.hint = hint
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(textColor)
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(2))
            isSingleLine = true
            background = GradientDrawable().apply {
                setColor(Color.rgb(250, 250, 247))
                setStroke(dp(1), borderColor)
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val hourField = timeField(savedHour, "HH")
        val minuteField = timeField(savedMinute, "MM")
        timeRow.addView(hourField, LinearLayout.LayoutParams(dp(76), dp(58)))
        timeRow.addView(text(":", 24f).apply {
            gravity = Gravity.CENTER
            setTextColor(textColor)
        }, LinearLayout.LayoutParams(dp(28), -2))
        timeRow.addView(minuteField, LinearLayout.LayoutParams(dp(76), dp(58)))
        val clockButton = Button(this).apply {
            text = "◷"
            textSize = 26f
            setTextColor(accent)
            background = outlinedButtonBackground(borderColor)
            contentDescription = "Selecionar horário no relógio"
            setPadding(dp(8), 0, dp(8), 0)
        }
        timeRow.addView(clockButton, LinearLayout.LayoutParams(dp(64), dp(52)).apply {
            marginStart = dp(12)
        })
        layout.addView(timeRow, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(16)
        })
        layout.addView(text("Digite a hora e os minutos, ou toque no relógio para selecionar.", 12f).apply {
            setTextColor(Color.rgb(100, 100, 94))
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        clockButton.setOnClickListener {
            val h = hourField.text.toString().toIntOrNull()?.coerceIn(0, 23) ?: savedHour
            val m = minuteField.text.toString().toIntOrNull()?.coerceIn(0, 59) ?: savedMinute
            TimePickerDialog(this, { _, selectedHour, selectedMinute ->
                hourField.setText(String.format(Locale.US, "%02d", selectedHour))
                minuteField.setText(String.format(Locale.US, "%02d", selectedMinute))
            }, h, m, true).show()
        }

        // Por padrão, salvar ativa o lembrete; o usuário pode desmarcar explicitamente.
        val enableCheck = CheckBox(this).apply {
            text = "Manter lembrete diário ativado"
            textSize = 15f
            setTextColor(textColor)
            isChecked = true
            setPadding(0, dp(4), 0, dp(8))
        }
        layout.addView(enableCheck, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(8)
        })

        val dialog = AlertDialog.Builder(this)
            .setTitle("Lembrete de leitura")
            .setView(layout)
            .setPositiveButton("Salvar", null)
            .setNeutralButton("Limpar seleção", null)
            .setNegativeButton("Fechar", null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val clearButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            val closeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            listOf(saveButton, clearButton, closeButton).forEach { button ->
                button.setTextColor(accent)
                button.background = outlinedButtonBackground(borderColor)
                button.setPadding(dp(10), dp(4), dp(10), dp(4))
                val params = button.layoutParams
                params.height = dp(44)
                button.layoutParams = params
            }
            saveButton.setOnClickListener {
                val hour = hourField.text.toString().toIntOrNull()
                val minute = minuteField.text.toString().toIntOrNull()
                if (hour == null || hour !in 0..23 || minute == null || minute !in 0..59) {
                    hourField.error = if (hour == null || hour !in 0..23) "Use 00 a 23" else null
                    minuteField.error = if (minute == null || minute !in 0..59) "Use 00 a 59" else null
                    return@setOnClickListener
                }
                if (enableCheck.isChecked) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4102)
                    }
                    reminderPrefs.edit().putBoolean("enabled", true)
                        .putInt("hour", hour).putInt("minute", minute).apply()
                    createReminderChannel()
                    scheduleBibleReminder(hour, minute)
                } else {
                    cancelBibleReminder()
                    reminderPrefs.edit().putBoolean("enabled", false)
                        .putInt("hour", hour).putInt("minute", minute).apply()
                }
                dialog.dismiss()
                showHome()
            }
            clearButton.setOnClickListener {
                cancelBibleReminder()
                reminderPrefs.edit().putBoolean("enabled", false).remove("hour").remove("minute").apply()
                dialog.dismiss()
                showHome()
            }
            closeButton.setOnClickListener { dialog.dismiss() }
        }
        dialog.show()
    }

    private fun outlinedButtonBackground(strokeColor: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.WHITE)
            setStroke(dp(1), strokeColor)
            cornerRadius = dp(8).toFloat()
        }

    private fun createReminderChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "leitura_biblia_diaria",
                "Lembrete diário da Bíblia",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Lembrete diário para fazer a leitura da Bíblia" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun scheduleBibleReminder(hour: Int, minute: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, BibleReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            this, 7101, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
    }

    private fun cancelBibleReminder() {
        val intent = Intent(this, BibleReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            this, 7101, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
        pending.cancel()
    }

    /** Ícones vetoriais simples: relógio, lista com tópicos e fluxo conectado. */
    private inner class ModeIconView(context: Context, private val iconType: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(105, 110, 108)
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val x = w * 0.12f
            val y = h * 0.12f
            when (iconType) {
                0 -> { // Relógio para lembrete
                    canvas.drawCircle(w * .5f, h * .5f, w * .34f, paint)
                    canvas.drawLine(w * .5f, h * .5f, w * .5f, h * .29f, paint)
                    canvas.drawLine(w * .5f, h * .5f, w * .68f, h * .59f, paint)
                    canvas.drawArc(w*.20f, h*.02f, w*.80f, h*.20f, 200f, 140f, false, paint)
                }
                1 -> { // Lista com tópicos: três pontos e linhas
                    val dotPaint = Paint(paint).apply { style = Paint.Style.FILL }
                    for (i in 0..2) {
                        val yy = h * (0.25f + i * 0.25f)
                        canvas.drawCircle(w * .17f, yy, dp(2).toFloat(), dotPaint)
                        canvas.drawLine(w * .34f, yy, w * .88f, yy, paint)
                    }
                }
                else -> { // Fluxo cronológico: nós conectados com setas
                    val path = Path().apply {
                        moveTo(w*.18f,h*.22f); lineTo(w*.48f,h*.22f); lineTo(w*.48f,h*.48f)
                        lineTo(w*.78f,h*.48f); lineTo(w*.78f,h*.75f)
                    }
                    canvas.drawPath(path, paint)
                    canvas.drawCircle(w*.18f,h*.22f,dp(2).toFloat(),paint)
                    canvas.drawCircle(w*.48f,h*.48f,dp(2).toFloat(),paint)
                    canvas.drawCircle(w*.78f,h*.75f,dp(2).toFloat(),paint)
                    canvas.drawLine(w*.68f,h*.65f,w*.78f,h*.75f,paint)
                    canvas.drawLine(w*.88f,h*.65f,w*.78f,h*.75f,paint)
                }
            }
        }
    }

    private fun showHome() {
        currentReading = null
        val completed = completedMap()
        val readings = if (chronologicalMode) ChronologicalBibleReadingData.all else BibleReadingData.all
        root = baseColumn()

        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(12))
            background = roundedBackground(Color.rgb(255, 190, 64), 2)
        }
        banner.addView(text("Leitura da Bíblia", 28f, true, Color.BLACK))
        banner.addView(text("P R O G R A M A", 14f, false, Color.BLACK))
        root.addView(banner, LinearLayout.LayoutParams(-1, -2))

        val doneCount = readings.count { completed.containsKey(it.id) }
        val totalCount = readings.size
        root.addView(text(
            "Hoje: ${today()}   •   Progresso: $doneCount/$totalCount (${if (totalCount == 0) 0 else (doneCount * 100.0 / totalCount).toInt().coerceAtLeast(if (doneCount > 0) 1 else 0)}%)",
            13f, true, Color.rgb(45, 65, 45)
        ).apply { setPadding(dp(4), dp(9), dp(4), dp(7)) })

        // Três ações compactas em uma única linha.
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun actionButton(label: String, color: Int, action: () -> Unit): TextView =
            TextView(this).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(35, 45, 36))
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(3), dp(10), dp(3), dp(10))
                background = roundedBackground(color, 8)
                elevation = dp(2).toFloat()
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
            }

        val gap = dp(5)
        actions.addView(actionButton("Apagar tudo", Color.rgb(255, 222, 190)) {
            confirmClearAll()
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = gap })
        actions.addView(actionButton("Não lidos", Color.rgb(225, 234, 218)) {
            if (selectedIds.isEmpty()) showSelectHint() else confirmResetSelected()
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = gap })
        actions.addView(actionButton("Marcar lidos", Color.rgb(207, 229, 207)) {
            if (selectedIds.isEmpty()) showSelectHint() else confirmMarkSelectedRead()
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        root.addView(actions, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(8)
        })

        val reminderPrefs = getSharedPreferences("lembrete_biblia", Context.MODE_PRIVATE)
        val reminderEnabled = reminderPrefs.getBoolean("enabled", false)
        val reminderHour = reminderPrefs.getInt("hour", 20)
        val reminderMinute = reminderPrefs.getInt("minute", 0)

        // Ações lado a lado com ícones desenhados em código, sem depender de imagens externas.
        val modeButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        fun modeButton(label: String, iconType: Int, action: () -> Unit): LinearLayout =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(7), dp(6), dp(7))
                background = roundedBackground(Color.rgb(224, 224, 224), 5)
                elevation = dp(1).toFloat()
                isClickable = true
                isFocusable = true
                val icon = ModeIconView(this@MainActivity, iconType).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                        rightMargin = dp(7)
                    }
                }
                addView(icon)
                addView(TextView(this@MainActivity).apply {
                    text = label
                    textSize = 10.5f
                    setTextColor(Color.rgb(40, 40, 40))
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER_VERTICAL
                    maxLines = 2
                }, LinearLayout.LayoutParams(0, -2, 1f))
                setOnClickListener { action() }
            }

        modeButtons.addView(modeButton(
            if (reminderEnabled) "Lembrete diário\n%02d:%02d · Alterar".format(reminderHour, reminderMinute)
            else "Agendar lembrete diário", 0
        ) { showReminderDialog() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            rightMargin = dp(5)
        })

        modeButtons.addView(modeButton(
            if (chronologicalMode) "Ordem dos livros bíblicos" else "Ordem cronológica",
            if (chronologicalMode) 1 else 2
        ) {
            chronologicalMode = !chronologicalMode
            prefs().edit().putBoolean("chronological_mode", chronologicalMode).apply()
            showHome()
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(modeButtons, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(8)
        })

        val columnHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(7), dp(4), dp(7))
            background = roundedBackground(Color.rgb(245, 245, 245), 6)
        }
        columnHeader.addView(text("DATA", 9f, true, Color.DKGRAY), LinearLayout.LayoutParams(dp(67), -2))
        columnHeader.addView(text("HORA", 9f, true, Color.DKGRAY), LinearLayout.LayoutParams(dp(43), -2))
        columnHeader.addView(text("TRECHO / CAPÍTULOS", 9f, true, Color.DKGRAY), LinearLayout.LayoutParams(0, -2, 1f))
        if (!chronologicalMode) {
            columnHeader.addView(text("✓", 13f, true, Color.DKGRAY), LinearLayout.LayoutParams(dp(28), -2))
        }
        root.addView(columnHeader, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(4)
        })

        // Na ordem habitual, mantém o cabeçalho fixo de seção/livro.
        // Na ordem cronológica, o único cabeçalho fixo é Data/Hora/Trecho.
        val stickyHeader = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(7), dp(8), dp(7))
            background = roundedBackground(sectionColor("OS ESCRITOS DE MOISÉS"), 8)
        }
        val stickySection = text("OS ESCRITOS DE MOISÉS", 13f, true)
        val stickyBook = text("GÊNESIS", 21f, true)
        stickyHeader.addView(stickySection)
        stickyHeader.addView(stickyBook)
        if (!chronologicalMode) {
            root.addView(stickyHeader, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(4)
            })
        }

        val scroll = ScrollView(this)
        scroll.isFillViewport = false
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val stickyMarkers = mutableListOf<Triple<View, String, String>>()
        val rowViews = mutableMapOf<String, View>()

        // O cabeçalho fixo já mostra a seção e o primeiro livro.
        // Portanto, omitimos o cabeçalho duplicado de Gênesis na lista rolável.
        // Os cabeçalhos dos livros seguintes continuam aparecendo normalmente.
        var currentSection = ""
        var currentBook = ""
        var firstBookHeader = true
        readings.forEach { reading ->
            val color = if (chronologicalMode) Color.WHITE else sectionColor(reading.section)
            if (!chronologicalMode && (reading.section != currentSection || reading.book != currentBook)) {
                currentSection = reading.section
                currentBook = reading.book

                if (!firstBookHeader) {
                    val marker = TextView(this).apply {
                        text = reading.section.uppercase(Locale("pt", "BR"))
                        textSize = 13f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(Color.rgb(35, 35, 25))
                        setPadding(dp(12), dp(9), dp(8), dp(9))
                        background = roundedBackground(color, 8)
                    }
                    list.addView(marker, LinearLayout.LayoutParams(-1, -2).apply {
                        topMargin = dp(7)
                        bottomMargin = dp(2)
                    })
                    val bookHeader = TextView(this).apply {
                        text = reading.book.uppercase(Locale("pt", "BR"))
                        textSize = 17f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(Color.rgb(40, 50, 40))
                        setPadding(dp(12), dp(9), dp(4), dp(9))
                        setBackgroundColor(color)
                    }
                    list.addView(bookHeader)
                    stickyMarkers.add(Triple(marker, reading.section, reading.book))
                }
                firstBookHeader = false
            }

            val doneDate = completed[reading.id]
            val planned = plannedDate(reading.id)
            val time = if (doneDate != null) completedTime(reading.id) else plannedTime(reading.id)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), dp(4), dp(2), dp(4))
                setBackgroundColor(color)
            }

            val dateText = text(doneDate ?: planned ?: "—", 10f, false).apply {
                gravity = Gravity.CENTER
                // Datas devem permanecer em uma única linha mesmo com fontes
                // maiores ou telas estreitas; se necessário, encurta visualmente.
                isSingleLine = true
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false
            }
            dateText.setOnClickListener { choosePlannedDate(reading.id) }
            row.addView(dateText, LinearLayout.LayoutParams(dp(78), dp(52)))

            val timeText = text(time ?: "—", 11f, false)
            timeText.gravity = Gravity.CENTER
            timeText.setOnClickListener { choosePlannedTime(reading.id) }
            row.addView(timeText, LinearLayout.LayoutParams(dp(52), dp(52)))

            // Capítulos e status ficam na mesma linha. "Lido" só aparece
            // quando existe uma data de conclusão.
            val chapterInfo = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(5), dp(2), dp(2), dp(2))
            }
            chapterInfo.addView(text(
                if (chronologicalMode) reading.reference else reading.chapters,
                if (chronologicalMode) 14f else 16f,
                false
            ))
            if (doneDate != null) {
                chapterInfo.addView(text("  Lido", 11f, true, Color.rgb(35, 105, 55)))
            }
            chapterInfo.setOnClickListener { showReading(reading) }
            row.addView(chapterInfo, LinearLayout.LayoutParams(0, -2, 1f))

            val check = CheckBox(this).apply {
                isChecked = selectedIds.contains(reading.id)
                buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(45, 70, 45))
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedIds.add(reading.id) else selectedIds.remove(reading.id)
                }
            }
            row.addView(check, LinearLayout.LayoutParams(dp(28), -2))

            val deleteDate = ImageView(this).apply {
                setImageResource(R.drawable.ic_trash)
                imageTintList = android.content.res.ColorStateList.valueOf(Color.rgb(105, 108, 108))
                contentDescription = "Apagar registro deste trecho"
                scaleType = ImageView.ScaleType.FIT_CENTER
                isClickable = true
                isFocusable = true
                setPadding(dp(3), dp(3), dp(3), dp(3))
                setOnClickListener { confirmDeleteRecord(reading.id, reading.reference) }
            }
            // Usa o ícone PNG enviado, ajustado ao mesmo quadrado da seleção.
            row.addView(deleteDate, LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                leftMargin = dp(2)
                rightMargin = dp(2)
            })

            row.addView(button("Ler") { showReading(reading) },
                LinearLayout.LayoutParams(dp(48), dp(42)))
            list.addView(row, LinearLayout.LayoutParams(-1, -2))
            rowViews[reading.id] = row
            list.addView(View(this).apply {
                setBackgroundColor(Color.rgb(180, 180, 160))
            }, LinearLayout.LayoutParams(-1, dp(1)))
        }

        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        // Ao abrir a tela inicial, posiciona o último trecho concluído no topo
        // da lista, para que seja o primeiro registro visível.
        scroll.post {
            val lastReadId = latestCompletedId(completed)
            val lastReadRow = lastReadId?.let { rowViews[it] }
            if (lastReadRow != null) {
                scroll.scrollTo(0, lastReadRow.top)
            }
        }

        if (!chronologicalMode) scroll.viewTreeObserver.addOnScrollChangedListener {
            // Se nenhum cabeçalho de livro posterior chegou ao topo da lista,
            // estamos no início: restaura o primeiro livro em vez de manter
            // o último cabeçalho visitado (por exemplo, Êxodo).
            val threshold = scroll.scrollY + dp(8)
            val current = stickyMarkers.lastOrNull { (view, _, _) -> view.top <= threshold }

            val section: String
            val book: String
            if (current != null) {
                section = current.second
                book = current.third
            } else {
                val firstReading = BibleReadingData.all.firstOrNull()
                section = firstReading?.section ?: "OS ESCRITOS DE MOISÉS"
                book = firstReading?.book ?: "Gênesis"
            }

            val newSection = section.uppercase(Locale("pt", "BR"))
            val newBook = book.uppercase(Locale("pt", "BR"))
            if (stickySection.text.toString() != newSection ||
                stickyBook.text.toString() != newBook) {
                stickySection.text = newSection
                stickyBook.text = newBook
                stickyHeader.background = roundedBackground(sectionColor(section), 8)
            }
        }
    }

    private fun showSelectHint() {
        AlertDialog.Builder(this)
            .setMessage("Selecione um ou mais trechos usando as caixas ao lado.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun confirmMarkSelectedRead() {
        AlertDialog.Builder(this)
            .setTitle("Marcar como lidos?")
            .setMessage("Os ${selectedIds.size} trechos selecionados serão registrados como concluídos hoje.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Marcar lidos") { _, _ ->
                val updated = completedMap()
                selectedIds.forEach { id ->
                    updated[id] = today()
                    prefs().edit().putString(completedTimePrefix + id, nowTime()).apply()
                }
                saveCompleted(updated)
                selectedIds.clear()
                showHome()
            }
            .show()
    }

    private fun choosePlannedDate(id: String) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            val date = String.format(Locale("pt", "BR"), "%02d/%02d/%04d", day, month + 1, year)
            prefs().edit().putString(plannedPrefix + id, date).apply()
            showHome()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun choosePlannedTime(id: String) {
        val cal = Calendar.getInstance()
        TimePickerDialog(this, { _, hour, minute ->
            val time = String.format(Locale("pt", "BR"), "%02d:%02d", hour, minute)
            prefs().edit().putString(plannedTimePrefix + id, time).apply()
            showHome()
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun confirmDeleteRecord(id: String, reference: String) {
        AlertDialog.Builder(this)
            .setTitle("Apagar registro")
            .setMessage("Apagar a data planejada, o horário e a conclusão de $reference?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Apagar") { _, _ ->
                prefs().edit()
                    .remove(plannedPrefix + id)
                    .remove(plannedTimePrefix + id)
                    .remove(completedTimePrefix + id)
                    .remove(completedPrefix + id)
                    .apply()
                showHome()
            }.show()
    }

    private fun confirmResetSelected() {
        AlertDialog.Builder(this)
            .setTitle("Marcar como não lido?")
            .setMessage("As datas de conclusão dos ${selectedIds.size} trechos selecionados serão apagadas. As datas planejadas serão mantidas.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Confirmar") { _, _ ->
                val updated = completedMap()
                selectedIds.forEach {
                    updated.remove(it)
                    prefs().edit().remove(completedTimePrefix + it).apply()
                }
                saveCompleted(updated)
                selectedIds.clear()
                showHome()
            }.show()
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle("Apagar tudo?")
            .setMessage("Todas as datas planejadas, horários e conclusões serão apagados. Todos os trechos voltarão para Não lido.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Apagar tudo") { _, _ ->
                prefs().edit().clear().apply()
                selectedIds.clear()
                showHome()
            }.show()
    }

    /**
     * Converte leituras com mais de um livro em referências que o WOL entende.
     * Ex.: "Obadias/Jonas" + "Livro completo" -> "Obadias 1-21; Jonas 1-4".
     * Para leituras comuns, preserva a pesquisa original.
     */
    private fun buildBibleSearchQuery(reading: BibleReading): String {
        val book = reading.book.trim()
        val chapters = reading.chapters.trim()
        if (reading.section == "ORDEM CRONOLÓGICA" && chapters.isBlank()) {
            return when (book) {
                "Obadias; Jeremias 1-2" -> "Obadias 1:1-21; Jeremias 1-2"
                "2 João / 3 João / Judas (completo)" ->
                    "2 João 1:1-13; 3 João 1:1-14; Judas 1:1-25"
                "Filemon; 1 Pedro 1-2" -> "Filêmon 1:1-25; 1 Pedro 1-2"
                "Filemon; 1 Pedro 3-5" -> "Filêmon 1:1-25; 1 Pedro 3-5"
                else -> book
            }
        }
        val completeBooks = mapOf(
            "Obadias/Jonas" to listOf("Obadias 1:1-21", "Jonas 1-4"),
            "Naum/Habacuque" to listOf("Naum 1-3", "Habacuque 1-3"),
            "Sofonias/Ageu" to listOf("Sofonias 1-3", "Ageu 1-2"),
            "Tito/Filêmon" to listOf("Tito 1-3", "Filêmon 1:1-25"),
            "2 João/3 João/Judas" to listOf("2 João 1:1-13", "3 João 1:1-14", "Judas 1:1-25")
        )

        if (chapters.equals("Livro completo", ignoreCase = true) ||
            chapters.equals("Livros completos", ignoreCase = true)) {
            completeBooks[book]?.let { return it.joinToString("; ") }
        }

        // Se futuramente houver outro grupo de livros separado por '/',
        // evita pesquisar o texto combinado como se fosse um único nome.
        if ('/' in book && (chapters.equals("Livro completo", true) ||
                    chapters.equals("Livros completos", true))) {
            return book.split('/').map { it.trim() }.joinToString("; ") { "$it 1" }
        }

        return "$book $chapters"
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showReading(reading: BibleReading) {
        currentReading = reading
        val queryText = buildBibleSearchQuery(reading)
        val query = URLEncoder.encode(queryText, "UTF-8")
        val url = "https://wol.jw.org/pt/wol/l/r5/lp-t?q=$query"

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        header.addView(button("← Voltar para a lista") { showHome() })
        header.addView(text(reading.reference, 20f, true))
        page.addView(header)

        // Área de leitura com indicador de carregamento enquanto o conteúdo
        // do WOL.JW.ORG está sendo buscado pela internet.
        val webContainer = FrameLayout(this)
        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webChromeClient = WebChromeClient()

        val loadingOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.WHITE)
        }
        val spinner = ProgressBar(this).apply {
            isIndeterminate = true
        }
        loadingOverlay.addView(spinner, LinearLayout.LayoutParams(dp(52), dp(52)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(16)
        })
        loadingOverlay.addView(text("Carregando", 20f, true, Color.rgb(45, 65, 45)).apply {
            gravity = Gravity.CENTER
        })
        loadingOverlay.addView(text("Verifique sua conexão com a Internet", 14f, false, Color.DKGRAY).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        })

        webContainer.addView(webView, FrameLayout.LayoutParams(-1, -1))
        webContainer.addView(loadingOverlay, FrameLayout.LayoutParams(-1, -1))
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                loadingOverlay.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loadingOverlay.visibility = View.GONE
            }

            @Suppress("DEPRECATION")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                loadingOverlay.visibility = View.GONE
                super.onReceivedError(view, errorCode, description, failingUrl)
            }
        }
        page.addView(webContainer, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
            setBackgroundColor(Color.WHITE)
            elevation = dp(5).toFloat()
        }
        footer.addView(text("Depois de terminar a leitura no site, toque abaixo.", 13f))
        footer.addView(button("Finalizar leitura") {
            val updated = completedMap()
            updated[reading.id] = today()
            saveCompleted(updated)
            prefs().edit().putString(completedTimePrefix + reading.id, nowTime()).apply()
            showHome()
        })
        page.addView(footer)

        // Em celulares com navegação por botões/gestos e edge-to-edge, reserva
        // espaço para a barra inferior do sistema para que ela não cubra
        // "Finalizar leitura". O espaço é aplicado ao rodapé, não ao WebView.
        val footerLeft = dp(12)
        val footerTop = dp(8)
        val footerRight = dp(12)
        val footerBottom = dp(12)
        page.setOnApplyWindowInsetsListener { _, insets ->
            val bottomInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            footer.setPadding(
                footerLeft,
                footerTop,
                footerRight,
                footerBottom + bottomInset
            )
            insets
        }
        page.requestApplyInsets()
        setContentView(page)
        webView.loadUrl(url)
    }
}
