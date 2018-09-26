/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.launcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.spark.launcher.CommandBuilderUtils.*;

/**
 * Command line interface for the Spark launcher. Used internally by Spark scripts.
 * Spark的启动命令行页面,由Spark脚本在内部使用
 */
class Main {

  /**
   * Usage: Main [class] [class args]
   * <p>
   * This CLI works in two different modes:    //包含两种模式
   * <ul>
   *   <li>"spark-submit": if <i>class</i> is "org.apache.spark.deploy.SparkSubmit", the
   *   {@link SparkLauncher} class is used to launch a Spark application.</li>
   *   <li>"spark-class": if another class is provided, an internal Spark class is run.</li>
   * </ul>
   *
   * This class works in tandem with the "bin/spark-class" script on Unix-like systems, and
   * "bin/spark-class2.cmd" batch script on Windows to execute the final command.
   * <p>
   * On Unix-like systems, the output is a list of command arguments, separated by the NULL
   * character. On Windows, the output is a command line suitable for direct execution from the
   * script.
   */
  public static void main(String[] argsArray) throws Exception {
    // 检查参数变量
    // spark-shell argsArray: org.apache.spark.deploy.SparkSubmit --class org.apache.spark.repl.Main --name Spark shell
    checkArgument(argsArray.length > 0, "Not enough arguments: missing class name.");

    /**
     * 命令参数样例(spark-shell)
     * java -cp spark_home/lib/spark-assembly-1.6.1-hadoop2.3.0.jar org.apache.spark.launcher.Main
     * org.apache.spark.deploy.SparkSubmit --class org.apache.spark.repl.Main
     * --name "Spark shell" --master spark://hdp115:7077
     */
    List<String> args = new ArrayList<String>(Arrays.asList(argsArray));
    String className = args.remove(0); // org.apache.spark.deploy.SparkSubmit

    // 可以通过设定环境变量 SPARK_PRINT_LAUNCH_COMMAND为任意值(不能为空)来打印
    // export SPARK_PRINT_LAUNCH_COMMAND=true
    boolean printLaunchCommand = !isEmpty(System.getenv("SPARK_PRINT_LAUNCH_COMMAND"));
    // args: --class org.apache.spark.repl.Main --name Spark shell
    AbstractCommandBuilder builder; // spark命令构建器抽象类,提供了一些通用功能
    // spark-submit提交
    if (className.equals("org.apache.spark.deploy.SparkSubmit")) {
      try {
        builder = new SparkSubmitCommandBuilder(args); // spark提交命令构建器对象,针对spark-submit脚本
      } catch (IllegalArgumentException e) { // 处理错误的命令行提交异常
        printLaunchCommand = false;
        System.err.println("Error: " + e.getMessage());
        System.err.println();

        MainClassOptionParser parser = new MainClassOptionParser();
        try {
          parser.parse(args);
        } catch (Exception ignored) {
          // Ignore parsing exceptions.
        }

        List<String> help = new ArrayList<String>();
        if (parser.className != null) {
          help.add(parser.CLASS);
          help.add(parser.className);
        }
        help.add(parser.USAGE_ERROR);
        builder = new SparkSubmitCommandBuilder(help);
      }
    } else {
      builder = new SparkClassCommandBuilder(className, args); // spark提交命令构建器对象,针对除spark-submit外的脚本
    }

    Map<String, String> env = new HashMap<String, String>();
    List<String> cmd = builder.buildCommand(env);
    if (printLaunchCommand) {
      System.err.println("Spark Command: " + join(" ", cmd));
      System.err.println("========================================");
    }
    // 打印输出,在spark-class脚本中进行执行
    if (isWindows()) {
      System.out.println(prepareWindowsCommand(cmd, env));
    } else {
      // In bash, use NULL as the arg separator since it cannot be used in an argument.
      // 返回至脚本命令参数(即spark-class脚本中的 ${CMD[@]} 值),接着执行
      List<String> bashCmd = prepareBashCommand(cmd, env);
      for (String c : bashCmd) {
        System.out.print(c);
        System.out.print('\0');
      }
    }
  }

  /**
   * Prepare a command line for execution from a Windows batch script.
   *
   * The method quotes all arguments so that spaces are handled as expected. Quotes within arguments
   * are "double quoted" (which is batch for escaping a quote). This page has more details about
   * quoting and other batch script fun stuff: http://ss64.com/nt/syntax-esc.html
   */
  private static String prepareWindowsCommand(List<String> cmd, Map<String, String> childEnv) {
    StringBuilder cmdline = new StringBuilder();
    for (Map.Entry<String, String> e : childEnv.entrySet()) {
      cmdline.append(String.format("set %s=%s", e.getKey(), e.getValue()));
      cmdline.append(" && ");
    }
    for (String arg : cmd) {
      cmdline.append(quoteForBatchScript(arg));
      cmdline.append(" ");
    }
    return cmdline.toString();
  }

  /**
   * Prepare the command for execution from a bash script. The final command will have commands to
   * set up any needed environment variables needed by the child process.
   */
  private static List<String> prepareBashCommand(List<String> cmd, Map<String, String> childEnv) {
    if (childEnv.isEmpty()) {
      return cmd;
    }

    List<String> newCmd = new ArrayList<String>();
    newCmd.add("env");

    for (Map.Entry<String, String> e : childEnv.entrySet()) {
      newCmd.add(String.format("%s=%s", e.getKey(), e.getValue()));
    }
    newCmd.addAll(cmd);
    return newCmd;
  }

  /**
   * A parser used when command line parsing fails for spark-submit. It's used as a best-effort
   * at trying to identify the class the user wanted to invoke, since that may require special
   * usage strings (handled by SparkSubmitArguments).
   */
  private static class MainClassOptionParser extends SparkSubmitOptionParser {

    String className;

    @Override
    protected boolean handle(String opt, String value) {
      if (CLASS.equals(opt)) {
        className = value;
      }
      return false;
    }

    @Override
    protected boolean handleUnknown(String opt) {
      return false;
    }

    @Override
    protected void handleExtraArgs(List<String> extra) {

    }

  }

}
