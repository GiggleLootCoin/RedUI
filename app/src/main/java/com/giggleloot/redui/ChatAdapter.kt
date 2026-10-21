package com.giggleloot.redui
import android.graphics.Color
import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
class ChatAdapter(private val items:List<ChatMessage>):RecyclerView.Adapter<ChatAdapter.V>(){class V(val t:TextView):RecyclerView.ViewHolder(t);override fun onCreateViewHolder(p:ViewGroup,v:Int)=V(TextView(p.context).apply{setTextColor(Color.WHITE);textSize=16f;setPadding(12,12,12,12)});override fun getItemCount()=items.size;override fun onBindViewHolder(h:V,p:Int){h.t.text=(if(items[p].role=="user")"YOU: " else "REDAI: ")+items[p].text}}