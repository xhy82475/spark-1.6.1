`spark-shell:`
/root/training/spark-2.2.1-bin-hadoop2.7/bin/spark-submit --class org.apache.spark.repl.Main --name Spark shell

`spark-submit:`
/root/training/spark-2.2.1-bin-hadoop2.7/bin/spark-class org.apache.spark.deploy.SparkSubmit --class org.apache.spark.repl.Main --name Spark shell

`spark-class:`
/root/training/jdk1.8.0_144/bin/java -cp /root/training/spark-2.2.1-bin-hadoop2.7/conf/:/root/training/spark-2.2.1-bin-hadoop2.7/jars/*:/root/training/hadoop-2.7.6/etc/hadoop/ -Dscala.usejavacp=true -Xmx1g org.apache.spark.deploy.SparkSubmit --class org.apache.spark.repl.Main --name Spark shell spark-shell

`Main:`
/root/training/jdk1.8.0_144/bin/java -Xmx128m -cp /root/training/spark-2.2.1-bin-hadoop2.7/jars/* org.apache.spark.launcher.Main org.apache.spark.deploy.SparkSubmit --class org.apache.spark.repl.Main --name Spark shell

**`spark-shell中SparkContext初始化流程:`**
org.apache.spark.launcher.Main -> org.apache.spark.deploy.SparkSubmit -> org.apache.spark.repl.Main -> org.apache.spark.repl.Main ->org.apache.spark.repl.SparkILoop -> createSparkContext()

