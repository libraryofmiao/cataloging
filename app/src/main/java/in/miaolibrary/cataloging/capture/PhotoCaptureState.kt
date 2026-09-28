package in.miaolibrary.cataloging.capture

enum class RequiredPhoto(val label:String){FRONT_COVER("Front cover"),TITLE_PAGE("Title page"),PUBLICATION_PAGE("Copyright / publication page"),ISBN_PAGE("ISBN / barcode page")}

data class PhotoCaptureState(val photos:Map<RequiredPhoto,String>=emptyMap()){
 fun isComplete()=RequiredPhoto.entries.all{photos[it]!=null}
 fun next():RequiredPhoto?=RequiredPhoto.entries.firstOrNull{photos[it]==null}
 fun add(type:RequiredPhoto,path:String)=copy(photos=photos+ (type to path))
}
