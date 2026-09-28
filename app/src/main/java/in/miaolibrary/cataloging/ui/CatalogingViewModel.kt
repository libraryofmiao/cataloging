package in.miaolibrary.cataloging.ui
import androidx.lifecycle.ViewModel
import in.miaolibrary.cataloging.catalog.Aacr2Normalizer
import in.miaolibrary.cataloging.marc.Marc21Builder
import in.miaolibrary.cataloging.model.CatalogRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ReviewState { CAPTURE, EXTRACT, MATCH, REVIEW, CONFIRM, COMPLETE }

class CatalogingViewModel:ViewModel(){
 private val _state=MutableStateFlow(ReviewState.CAPTURE)
 val state=_state.asStateFlow()
 private val _record=MutableStateFlow<CatalogRecord?>(null)
 val record=_record.asStateFlow()
 fun setDraft(r:CatalogRecord){_record.value=Aacr2Normalizer.normalize(r).record;_state.value=ReviewState.REVIEW}
 fun edit(r:CatalogRecord){_record.value=Aacr2Normalizer.normalize(r).record;_state.value=ReviewState.REVIEW}
 fun prepareConfirmation():Boolean{
  val r=_record.value?:return false
  val n=Aacr2Normalizer.normalize(r);_record.value=n.record
  if(!n.valid)return false
  _state.value=ReviewState.CONFIRM
  return true
 }
 fun buildMarc()=_record.value?.let(Marc21Builder::build)
}
