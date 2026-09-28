package `in`.miaolibrary.cataloging.barcode

import android.content.Context

class BarcodeSequence(context:Context){
 private val prefs=context.getSharedPreferences("cataloging_sequence",Context.MODE_PRIVATE)
 @Synchronized fun next():String{
  val next=prefs.getLong("next_barcode",1L)
  check(next<=999999999999L){"Barcode sequence exhausted"}
  prefs.edit().putLong("next_barcode",next+1).commit()
  return next.toString().padStart(6,'0')
 }
}
