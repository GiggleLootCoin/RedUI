package com.giggleloot.redui

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(
    private val items: List<ChatMessage>,
    private val prefs: SharedPreferences
) : RecyclerView.Adapter<ChatAdapter.V>() {

    class V(
        val row: LinearLayout,
        val avatar: ImageView,
        val bubble: LinearLayout,
        val label: TextView,
        val text: TextView
    ) : RecyclerView.ViewHolder(row)

    private fun Int.dp(view: android.view.View): Int =
        (this * view.resources.displayMetrics.density).toInt()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): V {
        val context = parent.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(4.dp(this), 6.dp(this), 4.dp(this), 6.dp(this))
        }
        val avatar = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(34.dp(this), 34.dp(this))
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val bubble = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp(this), 10.dp(this), 14.dp(this), 11.dp(this))
        }
        val label = TextView(context).apply {
            textSize = 10f
            letterSpacing = .08f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 4.dp(this))
        }
        val text = TextView(context).apply {
            setTextColor(Color.rgb(246, 243, 245))
            textSize = 15f
            setLineSpacing(2.dp(this).toFloat(), 1.02f)
            maxWidth = 276.dp(this)
        }
        bubble.addView(label)
        bubble.addView(text)
        return V(row, avatar, bubble, label, text)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: V, position: Int) {
        val item = items[position]
        val user = item.role == "user"
        holder.row.removeView(holder.avatar)
        holder.row.removeView(holder.bubble)
        holder.row.gravity = if (user) Gravity.END else Gravity.START
        val density = holder.row.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        holder.avatar.layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
            if (user) leftMargin = dp(8) else rightMargin = dp(8)
        }
        val accent = prefs.getInt("accent_color", Color.rgb(227, 27, 35))
        fun shade(color: Int, factor: Float) = Color.rgb(
            (Color.red(color) * factor).toInt().coerceIn(0, 255),
            (Color.green(color) * factor).toInt().coerceIn(0, 255),
            (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        )
        fun mix(first: Int, second: Int, amount: Float) = Color.rgb(
            (Color.red(first) * (1f - amount) + Color.red(second) * amount).toInt().coerceIn(0, 255),
            (Color.green(first) * (1f - amount) + Color.green(second) * amount).toInt().coerceIn(0, 255),
            (Color.blue(first) * (1f - amount) + Color.blue(second) * amount).toInt().coerceIn(0, 255)
        )
        holder.bubble.background = GradientDrawable().apply {
            setColor(if (user) shade(accent, .38f) else Color.rgb(25, 23, 27))
            setStroke(dp(1), if (user) accent else shade(accent, .38f))
            cornerRadius = dp(18).toFloat()
        }
        holder.label.text = if (user) "YOU" else "RED AI"
        holder.label.setTextColor(if (user) mix(accent, Color.WHITE, .55f) else accent)
        holder.text.text = item.text
        val avatarKey = if (user) "user_avatar" else "ai_avatar"
        val uri = prefs.getString(avatarKey, null)
        if (uri != null) {
            try { holder.avatar.setImageURI(Uri.parse(uri)) }
            catch (_: Exception) { holder.avatar.setImageDrawable(ColorDrawable(if (user) Color.DKGRAY else shade(accent, .62f))) }
        } else {
            holder.avatar.setImageDrawable(ColorDrawable(if (user) Color.DKGRAY else Color.rgb(150, 24, 38)))
        }
        val maxBubble = (holder.row.resources.displayMetrics.widthPixels - dp(90)).coerceAtLeast(dp(180))
        val bubbleParams = LinearLayout.LayoutParams(dp(276).coerceAtMost(maxBubble), -2)
        if (user) {
            holder.row.addView(holder.bubble, bubbleParams)
            holder.row.addView(holder.avatar)
        } else {
            holder.row.addView(holder.avatar)
            holder.row.addView(holder.bubble, bubbleParams)
        }
    }
}
