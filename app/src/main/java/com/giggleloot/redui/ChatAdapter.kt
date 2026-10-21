package com.giggleloot.redui
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.*
import android.widget.*
import android.content.SharedPreferences
import androidx.recyclerview.widget.RecyclerView
class ChatAdapter(private val items:List<ChatMessage>,private val prefs:SharedPreferences):RecyclerView.Adapter<ChatAdapter.V>(){
 class V(val row:LinearLayout,val avatar:ImageView,val text:TextView):RecyclerView.ViewHolder(row)
 override fun onCreateViewHolder(p:ViewGroup,v:Int)=V(LinearLayout(p.context).apply{orientation=LinearLayout.HORIZONTAL;setPadding(8,8,8,8)},ImageView(p.context).apply{layoutParams=LinearLayout.LayoutParams(52,52);scaleType=ImageView.ScaleType.CENTER_CROP},TextView(p.context).apply{setTextColor(Color.WHITE);textSize=16f;setPadding(12,8,12,8)})
 override fun getItemCount()=items.size
 override fun onBindViewHolder(h:V,p:Int){if(h.row.childCount==0){h.row.addView(h.avatar);h.row.addView(h.text,LinearLayout.LayoutParams(0,-2,1f))};val item=items[p];h.text.text=(if(item.role=="user")"YOU: " else "REDAI: ")+item.text;val key=if(item.role=="user")"user_avatar" else "ai_avatar";val uri=prefs.getString(key,null);if(uri!=null)try{h.avatar.setImageURI(Uri.parse(uri))}catch(_:Exception){h.avatar.setImageDrawable(ColorDrawable(Color.DKGRAY))}else h.avatar.setImageDrawable(ColorDrawable(if(item.role=="user")Color.DKGRAY else Color.rgb(130,20,30)))}
}