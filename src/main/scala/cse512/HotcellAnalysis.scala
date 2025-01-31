package cse512

import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions.udf
import org.apache.spark.sql.functions._

object HotcellAnalysis {
  Logger.getLogger("org.spark_project").setLevel(Level.WARN)
  Logger.getLogger("org.apache").setLevel(Level.WARN)
  Logger.getLogger("akka").setLevel(Level.WARN)
  Logger.getLogger("com").setLevel(Level.WARN)

def runHotcellAnalysis(spark: SparkSession, pointPath: String): DataFrame =
{
  // Load the original data from a data source
  var pickupInfo = spark.read.format("com.databricks.spark.csv").option("delimiter",";").option("header","false").load(pointPath);
  pickupInfo.createOrReplaceTempView("nyctaxitrips")
  pickupInfo.show()

  // Assign cell coordinates based on pickup points
  spark.udf.register("CalculateX",(pickupPoint: String)=>((
    HotcellUtils.CalculateCoordinate(pickupPoint, 0)
    )))
  spark.udf.register("CalculateY",(pickupPoint: String)=>((
    HotcellUtils.CalculateCoordinate(pickupPoint, 1)
    )))
  spark.udf.register("CalculateZ",(pickupTime: String)=>((
    HotcellUtils.CalculateCoordinate(pickupTime, 2)
    )))
  pickupInfo = spark.sql("select CalculateX(nyctaxitrips._c5),CalculateY(nyctaxitrips._c5), CalculateZ(nyctaxitrips._c1) from nyctaxitrips")
  var newCoordinateName = Seq("x", "y", "z")
  pickupInfo = pickupInfo.toDF(newCoordinateName:_*)
  pickupInfo.show()
  pickupInfo.createOrReplaceTempView("pickupinfo")
    
  // Define the min and max of x, y, z
  val minX = -74.50 / HotcellUtils.coordinateStep
  val maxX = -73.70 / HotcellUtils.coordinateStep
  val minY = 40.50 / HotcellUtils.coordinateStep
  val maxY = 40.90 / HotcellUtils.coordinateStep
  val minZ = 1
  val maxZ = 31
  val numCells = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1)

  val pts = spark.sql("select x,y,z from pickupinfo where x>=" + minX + " and x<= " + maxX + " and y>= " + minY + " and y<= " + maxY + " and z>= " + minZ + " and z<= " + maxZ + " order by z,y,x").persist();
  pts.createOrReplaceTempView("ptsView")

  val pts1 = spark.sql("select x,y,z,count(*) as pointval from ptsView group by z,y,x order by z,y,x").persist();
  pts1.createOrReplaceTempView("ptsView1")

  spark.udf.register("squared", (inputX: Int) => ((HotcellUtils.squared(inputX))))

  val sumPts = spark.sql("select count(*) as countval, sum(pointval) as sumval,sum(squared(pointval)) as squaredsum from ptsView1");
  sumPts.createOrReplaceTempView("sumPts")

  val mean =(sumPts.first().getLong(1).toDouble / numCells.toDouble).toDouble;
  val stdDev = math.sqrt(((sumPts.first().getDouble(2).toDouble / numCells.toDouble) - (mean.toDouble * mean.toDouble))).toDouble

  spark.udf.register("NumNeighbour", (minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int, inputX: Int, inputY: Int, inputZ: Int)=> ((HotcellUtils.NeighboursCount(minX, minY, minZ, maxX, maxY, maxZ, inputX, inputY, inputZ))))
  val Neighbours = spark.sql("select NumNeighbour("+minX + "," + minY + "," + minZ + "," + maxX + "," + maxY + "," + maxZ + "," + "a1.x,a1.y,a1.z) as nCount,count(*) as countall, a1.x as x,a1.y as y,a1.z as z, sum(a2.pointval) as sumtotal from ptsView1 as a1, ptsView1 as a2 where (a2.x = a1.x+1 or a2.x = a1.x or a2.x = a1.x-1) and (a2.y = a1.y+1 or a2.y = a1.y or a2.y =a1.y-1) and (a2.z = a1.z+1 or a2.z = a1.z or a2.z =a1.z-1) group by a1.z,a1.y,a1.x order by a1.z,a1.y,a1.x").persist()
  Neighbours.createOrReplaceTempView("NeighboursView");
  spark.udf.register("GValue", (x: Int, y: Int, z: Int, mean:Double, sd: Double, countn: Int, sumn: Int, numcells: Int) => ((HotcellUtils.GStat(x, y, z, mean, sd, countn, sumn, numcells))))
  
  val Neighbours1 = spark.sql("select GValue(x,y,z,"+mean+","+stdDev+",ncount,sumtotal,"+numCells+") as gtstat,x, y, z from NeighboursView order by gtstat desc");
  Neighbours1.createOrReplaceTempView("NeighboursView1")
      
  val result = spark.sql("select x,y,z from NeighboursView1")
  result.createOrReplaceTempView("finalresult")
  return result  
}
}
