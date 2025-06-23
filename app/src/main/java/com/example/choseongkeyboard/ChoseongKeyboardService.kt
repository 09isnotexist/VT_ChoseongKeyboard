package com.example.choseongkeyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.InputConnection
import android.graphics.Color
import android.widget.*
import android.view.Gravity
import android.view.ViewGroup
import android.view.KeyEvent

class ChoseongKeyboardService : InputMethodService() {

    private lateinit var rootLayout: LinearLayout
    private lateinit var preview: TextView
    private lateinit var suggestionRow: LinearLayout
    private lateinit var keyContainer: LinearLayout

    private var choseongBuffer = StringBuilder()
    private var dictionary: Map<String, List<String>> = emptyMap()
    private var isShifted = false
    private val composer = HangulComposer()

    enum class KeyboardMode { HANGUL, SYMBOL, ENGLISH }
    private var currentMode = KeyboardMode.HANGUL

    override fun onCreate(): Unit {
        super.onCreate()
        dictionary = loadDictionary()
    }

    override fun onCreateInputView(): View {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.DKGRAY)
        }

        preview = TextView(this).apply {
            text = "초성 입력 중..."
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(8, 8, 8, 8)
        }
        rootLayout.addView(preview)

        val scrollView = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        suggestionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        scrollView.addView(suggestionRow)
        rootLayout.addView(scrollView)

        keyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        rootLayout.addView(keyContainer)

        buildKeyboardLayout()

        return rootLayout
    }

    private fun buildKeyboardLayout() {
        keyContainer.removeAllViews()

        val layout = when (currentMode) {
            KeyboardMode.HANGUL -> listOf(
                listOf("1","2","3","4","5","6","7","8","9","0"),
                listOf("ㅂ","ㅈ","ㄷ","ㄱ","ㅅ","ㅛ","ㅕ","ㅑ"),
                listOf("ㅁ","ㄴ","ㅇ","ㄹ","ㅎ","ㅗ","ㅓ","ㅏ","ㅣ"),
                listOf("shift","ㅋ","ㅌ","ㅊ","ㅍ","ㅠ","ㅜ","ㅡ","delete"),
                listOf("ENG","SYM","space","enter")
            )
            KeyboardMode.ENGLISH -> {
                val base = listOf(
                    listOf("1","2","3","4","5","6","7","8","9","0"),
                    listOf("q","w","e","r","t","y","u","i","o","p"),
                    listOf("a","s","d","f","g","h","j","k","l"),
                    listOf("shift","z","x","c","v","b","n","m","delete"),
                    listOf("한","SYM","space","enter")
                )
                if (isShifted) base.map { row -> row.map { it.uppercase() } } else base
            }
            KeyboardMode.SYMBOL -> listOf(
                listOf("1","2","3","4","5","6","7","8","9","0"),
                listOf("!","@","#","$","%","^","&","*","(",")"),
                listOf("-","_","=","+","[","]","{","}","\\"),
                listOf("~","`","\"","'",":",";","<",">","delete"),
                listOf("한","ENG","space","enter")
            )
        }

        for (rowKeys in layout) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                weightSum = rowKeys.size.toFloat()
            }

            for (key in rowKeys) {
                val keyView = TextView(this).apply {
                    text = when (key.lowercase()) {
                        "space" -> "␣"
                        "delete" -> "⌫"
                        "enter" -> "⏎"
                        "shift" -> if (isShifted) "⇩" else "⇧"
                        else -> getDisplayKey(key)
                    }
                    gravity = Gravity.CENTER
                    setBackgroundColor(Color.LTGRAY)
                    setTextColor(Color.BLACK)
                    textSize = 16f
                    setPadding(16, 16, 16, 16)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(2, 2, 2, 2)
                    }
                    setOnClickListener { handleKeyPress(key) }
                }
                row.addView(keyView)
            }
            keyContainer.addView(row)
        }
    }

    private fun handleKeyPress(key: String) {
        when (key.lowercase()) {
            "space" -> {
                commitText(composer.commit())
                replaceComposingText("")
                commitText(" ")
            }
            "enter" -> {
                commitText(composer.commit())
                replaceComposingText("")
                sendEnterKey()
            }
            "delete" -> {
                if (composer.isComposing()) {
                    composer.delete()
                    replaceComposingText(composer.getComposedText())
                } else {
                    currentInputConnection.deleteSurroundingText(1, 0)
                    if (choseongBuffer.isNotEmpty()) {
                        choseongBuffer.deleteCharAt(choseongBuffer.length - 1)
                        updatePreview()
                        clearSuggestions()
                    }
                }
            }
            "한" -> {
                commitText(composer.commit())
                replaceComposingText("")
                currentMode = KeyboardMode.HANGUL
                buildKeyboardLayout()
            }
            "eng" -> {
                commitText(composer.commit())
                replaceComposingText("")
                currentMode = KeyboardMode.ENGLISH
                buildKeyboardLayout()
            }
            "sym" -> {
                commitText(composer.commit())
                replaceComposingText("")
                currentMode = KeyboardMode.SYMBOL
                buildKeyboardLayout()
            }
            "shift" -> {
                isShifted = !isShifted
                buildKeyboardLayout()
            }
            else -> {
                val finalKey = if (isShifted && currentMode == KeyboardMode.HANGUL) convertToDoubleConsonant(key) else key

                if (currentMode == KeyboardMode.HANGUL && finalKey.matches(Regex("[ㄱ-ㅎㅏ-ㅣ]"))) {
                    if (finalKey.matches(Regex("[ㅏ-ㅣ]"))) {
                        // 모음 입력 시 초성 제안 종료
                        choseongBuffer.clear()
                        preview.text = ""
                        clearSuggestions()
                    }
                    composer.addJamo(finalKey)
                    if (finalKey.matches(Regex("[ㄱ-ㅎ]"))) {
                        val ic = currentInputConnection
                        val before = ic.getTextBeforeCursor(1, 0)?.lastOrNull()
                        if (before == null || before == ' ') {
                            choseongBuffer.append(finalKey)
                            if (choseongBuffer.length >= 2) {
                                updatePreview()
                                val suggestions = dictionary[choseongBuffer.toString()]
                                if (suggestions != null) showSuggestions(suggestions) else clearSuggestions()
                            } else {
                                clearSuggestions()
                            }
                        } else {
                            choseongBuffer.clear()
                            clearSuggestions()
                        }
                        updatePreview()
                        val suggestions = dictionary[choseongBuffer.toString()]
                        if (suggestions != null) showSuggestions(suggestions) else clearSuggestions()
                    } else {
                        choseongBuffer.clear()
                        clearSuggestions()
                    }
                    replaceComposingText(composer.getComposedText())
                } else {
                    commitText(composer.commit())
                    replaceComposingText("")
                    commitText(finalKey)
                }
            }
        }
    }

    private fun convertToDoubleConsonant(ch: String): String {
        return when (ch) {
            "ㄱ" -> "ㄲ"
            "ㄷ" -> "ㄸ"
            "ㅂ" -> "ㅃ"
            "ㅅ" -> "ㅆ"
            "ㅈ" -> "ㅉ"
            else -> ch
        }
    }

    private fun getDisplayKey(key: String): String {
        return if (currentMode == KeyboardMode.HANGUL && isShifted) {
            when (key) {
                "ㄱ" -> "ㄲ"
                "ㄷ" -> "ㄸ"
                "ㅂ" -> "ㅃ"
                "ㅅ" -> "ㅆ"
                "ㅈ" -> "ㅉ"
                else -> key
            }
        } else key
    }

    private fun commitText(text: String) {
        currentInputConnection.commitText(text, 1)
    }

    private fun replaceComposingText(text: String) {
        currentInputConnection.setComposingText(text, 1)
    }

    private fun sendEnterKey() {
        val eventTime = System.currentTimeMillis()
        currentInputConnection.sendKeyEvent(
            KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0)
        )
        currentInputConnection.sendKeyEvent(
            KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0)
        )
    }

    private fun updatePreview() {
        preview.text = "입력: $choseongBuffer"
    }

    private fun clearSuggestions() {
        suggestionRow.removeAllViews()
    }

    private fun showSuggestions(words: List<String>) {
        clearSuggestions()
        words.forEach { word ->
            val btn = Button(this).apply {
                text = word
                setOnClickListener {
                    commitText(word)
                    choseongBuffer.clear()
                    updatePreview()
                    clearSuggestions()
                }
            }
            suggestionRow.addView(btn)
        }
    }

    private fun loadDictionary(): Map<String, List<String>> {
        return try {
            val inputStream = assets.open("choseong_dict.json")
            val json = inputStream.bufferedReader().use { it.readText() }
            val result = mutableMapOf<String, List<String>>()
            val jsonObj = org.json.JSONObject(json)
            for (key in jsonObj.keys()) {
                val array = jsonObj.getJSONArray(key)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
                result[key] = list
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }
}

class HangulComposer {
    private val choseong = listOf(
        'ㄱ','ㄲ','ㄴ','ㄷ','ㄸ','ㄹ','ㅁ','ㅂ','ㅃ','ㅅ','ㅆ','ㅇ','ㅈ','ㅉ','ㅊ','ㅋ','ㅌ','ㅍ','ㅎ'
    )
    private val jungseong = listOf(
        'ㅏ','ㅐ','ㅑ','ㅒ','ㅓ','ㅔ','ㅕ','ㅖ','ㅗ','ㅘ','ㅙ','ㅚ','ㅛ','ㅜ','ㅝ','ㅞ','ㅟ','ㅠ','ㅡ','ㅢ','ㅣ'
    )
    private val jongseong = listOf(
        ' ', 'ㄱ','ㄲ','ㄳ','ㄴ','ㄵ','ㄶ','ㄷ','ㄹ','ㄺ','ㄻ','ㄼ','ㄽ','ㄾ','ㄿ','ㅀ','ㅁ','ㅂ','ㅄ','ㅅ','ㅆ','ㅇ','ㅈ','ㅊ','ㅋ','ㅌ','ㅍ','ㅎ'
    )

    private var buffer = mutableListOf<Char>()

    fun addJamo(jamo: String) {
        if (jamo.length == 1) buffer.add(jamo[0])
    }

    fun getComposedText(): String {
        return compose(buffer)?.toString() ?: buffer.joinToString("")
    }

    fun isComposing(): Boolean = buffer.isNotEmpty()

    fun delete() {
        if (buffer.isNotEmpty()) buffer.removeAt(buffer.size - 1)
    }

    fun commit(): String {
        val text = getComposedText()
        buffer.clear()
        return text
    }

    private fun compose(buf: List<Char>): Char? {
        if (buf.size < 2) return null
        val c = choseong.indexOf(buf[0])
        val j = jungseong.indexOf(buf[1])
        val t = if (buf.size > 2) jongseong.indexOf(buf[2]) else 0
        if (c < 0 || j < 0 || t < 0) return null
        return (0xAC00 + (c * 21 * 28) + (j * 28) + t).toChar()
    }
}
