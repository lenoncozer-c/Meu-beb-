package com.meubebe.app

import android.app.*
import android.os.*
import android.content.*
import android.database.sqlite.*
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*
import java.io.File
import kotlin.math.*

class BabyDb(ctx: Context): SQLiteOpenHelper(ctx,"baby.db",null,1) {
 override fun onCreate(db:SQLiteDatabase) {
  db.execSQL("CREATE TABLE profile(id INTEGER PRIMARY KEY, name TEXT, birth INTEGER, weight REAL)")
  db.execSQL("CREATE TABLE logs(id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, started INTEGER, ended INTEGER, amount REAL, note TEXT)")
  db.execSQL("CREATE TABLE weights(id INTEGER PRIMARY KEY AUTOINCREMENT, date INTEGER, value REAL)")
 }
 override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){}
 fun log(type:String,started:Long,ended:Long=0L,amount:Double=0.0,note:String=""){
  writableDatabase.execSQL("INSERT INTO logs(type,started,ended,amount,note) VALUES(?,?,?,?,?)",arrayOf(type,started,ended,amount,note))
 }
 fun rows(table:String,order:String):List<Map<String,String>>{
  val list=mutableListOf<Map<String,String>>()
  readableDatabase.rawQuery("SELECT * FROM $table ORDER BY $order",null).use{c->
   while(c.moveToNext()){val r=mutableMapOf<String,String>();for(i in 0 until c.columnCount)r[c.getColumnName(i)]=c.getString(i)?:"";list.add(r)}
  }
  return list
 }
}
class MainActivity:Activity(){
 private lateinit var db:BabyDb
 private lateinit var body:LinearLayout
 private val dt=SimpleDateFormat("dd/MM/yyyy HH:mm",Locale("pt","BR"))
 private val dd=SimpleDateFormat("dd/MM/yyyy",Locale("pt","BR"))
 private val accent=Color.rgb(118,83,171)
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);db=BabyDb(this);show("Início")}
 private fun txt(s:String,size:Float=17f):TextView=TextView(this).apply{text=s;textSize=size;setTextColor(Color.rgb(52,42,68));setPadding(12,14,12,14)}
 private fun button(s:String,action:()->Unit):Button=Button(this).apply{text=s;setTextColor(Color.WHITE);backgroundTintList=android.content.res.ColorStateList.valueOf(accent);setOnClickListener{action()}}
 private fun input(h:String,num:Boolean=false)=EditText(this).apply{hint=h;if(num)inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL}
 private fun add(v:View){body.addView(v)}
 private fun title(s:String){add(txt(s,23f))}
 private fun notify(s:String){Toast.makeText(this,s,Toast.LENGTH_LONG).show()}
 private fun logs()=db.rows("logs","started DESC")
 private fun profile()=db.rows("profile","id DESC").firstOrNull()
 private fun midnight(t:Long):Long=Calendar.getInstance().apply{timeInMillis=t;set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis
 private fun show(page:String){
  val frame=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(251,248,253))}
  frame.addView(txt("♥  Meu Bebê",27f).apply{setTextColor(accent)})
  val scrollNav=HorizontalScrollView(this);val nav=LinearLayout(this)
  listOf("Início","Sono","Mamadas","Fraldas","Peso","Relatórios","Perfil").forEach{p->nav.addView(button(p){show(p)})}
  scrollNav.addView(nav);frame.addView(scrollNav)
  val sc=ScrollView(this);body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(12,8,12,24)}
  sc.addView(body);frame.addView(sc,LinearLayout.LayoutParams(-1,0,1f));setContentView(frame)
  when(page){"Início"->home();"Sono"->sleep();"Mamadas"->feeds();"Fraldas"->diapers();"Peso"->weights();"Relatórios"->report();"Perfil"->editProfile()}
 }
 private fun chooseTime(label:String,initial:Long=System.currentTimeMillis(),done:(Long)->Unit){
  val f=input("dd/MM/aaaa HH:mm").apply{setText(dt.format(Date(initial)))}
  AlertDialog.Builder(this).setTitle(label).setView(f).setPositiveButton("Salvar"){_,_->val t=try{dt.parse(f.text.toString())?.time}catch(e:Exception){null};if(t==null)notify("Data inválida")else done(t)}.setNegativeButton("Cancelar",null).show()
 }
 private fun home(){
  title("Resumo de hoje");val p=profile()
  if(p==null)add(button("Cadastrar bebê"){show("Perfil")})
  else{val age=((System.currentTimeMillis()-(p["birth"]?.toLongOrNull()?:0L))/86400000).coerceAtLeast(0);add(txt("${p["name"]} • $age dias de vida",20f))}
  val now=System.currentTimeMillis();val today=midnight(now);val entries=logs()
  fun sleep(a:Long,b:Long)=entries.filter{it["type"]=="sono"}.sumOf{r->val s=r["started"]!!.toLong();val e=r["ended"]!!.toLong().takeIf{it>0}?:now;(min(e,b)-max(s,a)).coerceAtLeast(0L)}
  fun duration(t:Long)="${t/3600000}h ${(t%3600000)/60000}min"
  add(txt("Sono hoje: ${duration(sleep(today,now))}"))
  add(txt("Sono ontem: ${duration(sleep(today-86400000L,today))}"))
  add(txt("Mamadas hoje: ${entries.count{it["type"]=="mamada"&&it["started"]!!.toLong()>=today}}"))
  add(txt("Fraldas hoje: ${entries.count{it["type"]=="fralda"&&it["started"]!!.toLong()>=today}}"))
 }
 private fun sleep(){
  title("Sono");val active=logs().firstOrNull{it["type"]=="sono"&&it["ended"]=="0"}
  if(active!=null){add(txt("Dormindo desde ${dt.format(Date(active["started"]!!.toLong()))}"));add(button("Acordou agora"){db.writableDatabase.execSQL("UPDATE logs SET ended=? WHERE id=?",arrayOf(System.currentTimeMillis(),active["id"]));show("Sono")})}
  else add(button("Começou a dormir"){db.log("sono",System.currentTimeMillis());show("Sono")})
  add(button("Registrar sono anterior"){chooseTime("Início"){s->chooseTime("Fim",s+3600000){e->if(e>s){db.log("sono",s,e);show("Sono")}else notify("Fim anterior ao início")}}})
  history("sono")
 }
 private fun feeds(){
  title("Mamadas");val ml=input("Quantidade (ml)",true);add(ml)
  val type=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Fórmula","Leite materno"))};add(type)
  fun save(t:Long){val amount=ml.text.toString().replace(',','.').toDoubleOrNull();if(amount==null||amount<=0)notify("Informe a quantidade")else{db.log("mamada",t,amount=amount,note=type.selectedItem.toString());show("Mamadas")}}
  add(button("Salvar agora"){save(System.currentTimeMillis())})
  add(button("Outro horário"){chooseTime("Horário da mamada"){save(it)}});history("mamada")
 }
 private fun diapers(){
  title("Fraldas");listOf("Xixi","Cocô","Misto").forEach{kind->add(button(kind){db.log("fralda",System.currentTimeMillis(),note=kind);show("Fraldas")})}
  add(button("Outro horário"){AlertDialog.Builder(this).setItems(arrayOf("Xixi","Cocô","Misto")){_,i->chooseTime("Horário da troca"){db.log("fralda",it,note=listOf("Xixi","Cocô","Misto")[i]);show("Fraldas")}}.show()});history("fralda")
 }
 private fun history(kind:String){
  title("Histórico")
  logs().filter{it["type"]==kind}.take(100).forEach{r->
   val desc=dt.format(Date(r["started"]!!.toLong()))+" • "+when(kind){"sono"->if(r["ended"]=="0")"em andamento" else "até "+dt.format(Date(r["ended"]!!.toLong()));"mamada"->"${r["amount"]} ml • ${r["note"]}";else->r["note"].orEmpty()}
   val line=LinearLayout(this);line.addView(txt(desc,14f),LinearLayout.LayoutParams(0,-2,1f))
   line.addView(Button(this).apply{text="Excluir";setOnClickListener{AlertDialog.Builder(this@MainActivity).setMessage("Excluir registro?").setPositiveButton("Excluir"){_,_->db.writableDatabase.delete("logs","id=?",arrayOf(r["id"]));show(when(kind){"sono"->"Sono";"mamada"->"Mamadas";else->"Fraldas"})}.setNegativeButton("Cancelar",null).show()}});add(line)
  }
 }
 private fun weights(){
  title("Peso");val f=input("Peso em kg",true);add(f)
  fun save(t:Long){val v=f.text.toString().replace(',','.').toDoubleOrNull();if(v==null||v<=0)notify("Peso inválido")else{db.writableDatabase.execSQL("INSERT INTO weights(date,value) VALUES(?,?)",arrayOf(t,v));show("Peso")}}
  add(button("Salvar pesagem"){save(System.currentTimeMillis())});add(button("Outra data"){chooseTime("Data da pesagem"){save(it)}})
  val entries=db.rows("weights","date ASC")
  if(entries.isNotEmpty()){add(Graph(this,entries.map{it["value"]!!.toFloat()},"Evolução do peso"))
   if(entries.size>1){val first=entries.first();val last=entries.last();val days=(last["date"]!!.toLong()-first["date"]!!.toLong())/86400000.0
    if(days>=7){val rate=(last["value"]!!.toDouble()-first["value"]!!.toDouble())/days;add(txt("Projeção linear em 30 dias: %.2f kg".format(Locale.US,last["value"]!!.toDouble()+rate*30)));add(txt("Estimativa matemática; consulte o pediatra."))}}
  }
  entries.asReversed().forEach{r->add(button("${dt.format(Date(r["date"]!!.toLong()))}: ${r["value"]} kg  •  Excluir"){AlertDialog.Builder(this).setMessage("Excluir pesagem?").setPositiveButton("Excluir"){_,_->db.writableDatabase.delete("weights","id=?",arrayOf(r["id"]));show("Peso")}.setNegativeButton("Cancelar",null).show()})}
 }
 private fun editProfile(){
  title("Perfil do bebê");val p=profile()
  val name=input("Nome").apply{setText(p?.get("name")?:"")}
  val birth=input("Nascimento: dd/MM/aaaa").apply{setText(p?.get("birth")?.toLongOrNull()?.let{dd.format(Date(it))}?:"")}
  val weight=input("Peso ao nascer (kg)",true).apply{setText(p?.get("weight")?:"")}
  add(name);add(birth);add(weight)
  add(button("Salvar perfil"){val date=try{dd.parse(birth.text.toString())?.time}catch(e:Exception){null};val kg=weight.text.toString().replace(',','.').toDoubleOrNull()
   if(name.text.isBlank()||date==null||date>System.currentTimeMillis()||kg==null||kg<=0)notify("Confira os dados")
   else{db.writableDatabase.execSQL("DELETE FROM profile");db.writableDatabase.execSQL("INSERT INTO profile(name,birth,weight) VALUES(?,?,?)",arrayOf(name.text.toString(),date,kg));show("Início")}
  })
 }
 private fun report(){
  title("Relatórios e médias");val periods=listOf(7,15,30,90,0)
  val spinner=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("7 dias","15 dias","30 dias","90 dias","Todo histórico"))};add(spinner)
  val result=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};add(result)
  spinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
   override fun onNothingSelected(parent:AdapterView<*>?){}
   override fun onItemSelected(parent:AdapterView<*>?,v:View?,pos:Int,id:Long){
    result.removeAllViews();val now=System.currentTimeMillis();val entries=logs();val earliest=entries.minOfOrNull{it["started"]!!.toLong()}?:now
    val start=if(periods[pos]==0)midnight(earliest) else midnight(now)-(periods[pos]-1)*86400000L
    val n=((midnight(now)-start)/86400000L+1).toInt().coerceAtLeast(1)
    val sleeps=DoubleArray(n);val feeds=DoubleArray(n);val diapers=DoubleArray(n)
    for(i in 0 until n){val a=start+i*86400000L;val z=a+86400000L
     sleeps[i]=entries.filter{it["type"]=="sono"}.sumOf{r->val s=r["started"]!!.toLong();val e=r["ended"]!!.toLong().takeIf{it>0}?:now;(min(e,min(z,now))-max(s,a)).coerceAtLeast(0L)}/3600000.0
     feeds[i]=entries.count{it["type"]=="mamada"&&it["started"]!!.toLong() in a until z}.toDouble()
     diapers[i]=entries.count{it["type"]=="fralda"&&it["started"]!!.toLong() in a until z}.toDouble()
    }
    val ml=entries.filter{it["type"]=="mamada"&&it["started"]!!.toLong()>=start}.sumOf{it["amount"]!!.toDouble()}
    listOf("Período: $n dias","Sono médio: %.1f h/dia".format(Locale.US,sleeps.average()),"Mamadas: ${feeds.sum().toInt()} • %.1f/dia".format(Locale.US,feeds.average()),"Volume: %.0f ml".format(Locale.US,ml),"Fraldas: ${diapers.sum().toInt()} • %.1f/dia".format(Locale.US,diapers.average())).forEach{result.addView(txt(it))}
    result.addView(Graph(this@MainActivity,sleeps.map{it.toFloat()},"Sono (h/dia)"));result.addView(Graph(this@MainActivity,feeds.map{it.toFloat()},"Mamadas/dia"));result.addView(Graph(this@MainActivity,diapers.map{it.toFloat()},"Fraldas/dia"))
    result.addView(button("Exportar PDF"){exportPdf(n,sleeps,feeds,diapers,ml)})
   }
  }
 }
 private fun exportPdf(n:Int,s:DoubleArray,f:DoubleArray,d:DoubleArray,ml:Double){
  val doc=PdfDocument();val pg=doc.startPage(PdfDocument.PageInfo.Builder(595,842,1).create());val c=pg.canvas;val paint=Paint(3).apply{color=Color.DKGRAY;textSize=17f};var y=60f
  fun line(t:String){c.drawText(t,35f,y,paint);y+=42f}
  line("Meu Bebe - Relatorio");paint.textSize=14f;line("Gerado em ${dt.format(Date())}");line("Periodo: $n dias")
  line("Sono medio: %.1f h/dia".format(Locale.US,s.average()));line("Mamadas: ${f.sum().toInt()}");line("Volume: %.0f ml".format(Locale.US,ml));line("Fraldas: ${d.sum().toInt()}")
  line("Peso: ${db.rows("weights","date DESC").firstOrNull()?.get("value")?:"nao informado"} kg")
  doc.finishPage(pg)
  try{val dir=getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?:filesDir;val file=File(dir,"MeuBebe_${System.currentTimeMillis()}.pdf");file.outputStream().use{doc.writeTo(it)}
   AlertDialog.Builder(this).setMessage("PDF salvo em:\n${file.absolutePath}").setPositiveButton("OK",null).show()
  }catch(e:Exception){notify("Erro ao salvar PDF: ${e.message}")}finally{doc.close()}
 }
}
class Graph(ctx:Context,private val values:List<Float>,private val label:String):View(ctx){
 private val paint=Paint(3)
 override fun onMeasure(w:Int,h:Int){setMeasuredDimension(MeasureSpec.getSize(w),220)}
 override fun onDraw(c:Canvas){super.onDraw(c);paint.color=Color.DKGRAY;paint.textSize=29f;c.drawText(label,18f,35f,paint)
  if(values.isEmpty())return
  val max=values.maxOrNull()?.coerceAtLeast(1f)?:1f;val left=20f;val right=width-20f;val top=65f;val bottom=height-25f
  paint.color=Color.LTGRAY;c.drawLine(left,bottom,right,bottom,paint);paint.color=Color.rgb(118,83,171);paint.strokeWidth=4f
  if(values.size==1)c.drawCircle(left,bottom-values[0]/max*(bottom-top),5f,paint)
  for(i in 1 until values.size){val x1=left+(right-left)*(i-1)/(values.size-1);val x2=left+(right-left)*i/(values.size-1)
   c.drawLine(x1,bottom-values[i-1]/max*(bottom-top),x2,bottom-values[i]/max*(bottom-top),paint)}
 }
}
