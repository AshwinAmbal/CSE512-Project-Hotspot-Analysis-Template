package cse512

object HotzoneUtils {

  def ST_Contains(queryRectangle: String, pointString: String ): Boolean = {
    val rect = queryRectangle.split(",")
    val pt = pointString.split(",")
    val rectangle = rect.map(_.toDouble)
    val point = pt.map(_.toDouble) 
    
	val minX =  if(rectangle(0) < rectangle(2)) rectangel(0) else rectangle(2)
    val minY =  if(rectangle(1) < rectangle(3)) rectangel(1) else rectangle(3)
    val maxX =  if(rectangle(0) < rectangle(2)) rectangle(2) else rectangle(0)
    val maxY =  if(rectangle(1) < rectangle(3)) rectangel(3) else rectangle(1)
	
	if (point(0) >= minX && point(1) >= minY && point(0) <= maxX && point(1) <= maxY ){
        return true
    }
    return false
  }

  

}
