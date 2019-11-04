import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Debug;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;


import com.afwsamples.testdpc.Application;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Aspect
public final class AspectDebugLog {
    //Line형태의 시퀀스 로그
    private static final String ASPECT_LOG_SEQUENCE_TYPE = "ALOGSEQ";

    //PlantUML에 이용하기 위한 로그
    private static final String ASPECT_LOG_PLANTUML_TYPE = "ALOGUML";

    //버퍼를 모아서 디비에 출력하기용.
    private static int logBufferCount = 1;
    //Limit 만큼 카운트가 되면 디비에 저장.
    private static int logBufferStoreCountLimit = 3000;

    //버퍼를 모아서 디비에 출력하기용. 프로비저닝 등의 작업에서는 로그출력이 불가능하니까 사용
    private static String logBufferMessages = "";

    private static HashMap<Integer, List<String>> depthTexts = new HashMap<>();
    private static HashMap<Integer, Integer> depthCounts = new HashMap<>();
    private static HashMap<Integer, Integer> processColor = new HashMap<>();

    private static String verticalLine = "│";
    private String lastClassMethod = "Start";


    private static final String START = "Start";
    private static final String ACTIVATE = "activate ";
    private static final String DEACTIVATE = "deactivate ";

    private int logType1 = 1;
    private String argAll;

    private static final String POINTCUT_BEFORE_METHOD =
            "(" +
                    "execution(* com.afwsamples.testdpc..*.*(..)) " +
                    ")" +
                    " && !(" +
                    "execution(* com.afwsamples.testdpc.Application*..*(..))" +
                    ")";

    private static final String POINTCUT_AROUND_METHOD = POINTCUT_BEFORE_METHOD;

    @Pointcut(POINTCUT_BEFORE_METHOD)
    public void pointcutDebugTraceBefore() {}

    @Pointcut(POINTCUT_AROUND_METHOD)
    public void pointcutDebugTraceAround() {}

    @Before("pointcutDebugTraceBefore()")
    public void weaveDebugTraceBefore(JoinPoint joinPoint) throws Throwable {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        String currentClassName = utils.getOnlyClassName(methodSignature.getDeclaringType().getSimpleName());
        String currentMethodName = utils.getOnlyClassName(methodSignature.getName());
        //not use
        argAll = utils.getArgs(joinPoint, methodSignature);

        int tid = android.os.Process.myTid();

        List<String> dummyDepthText = utils.getdepthTexts(tid);

        lastClassMethod = utils.getLastClassMethod(lastClassMethod, dummyDepthText, logType1);

        if (utils.getdepthCounts(tid) < 1) {
            lastClassMethod = "Start";
        }

        LogPrinter.logBefore(currentClassName, currentMethodName, lastClassMethod, tid);
        LogPrinter.logBeforeArg(lastClassMethod, tid, argAll);


        printTo(type2PrintTid(tid) + ASPECT_LOG_SEQUENCE_TYPE, utils.repeat(verticalLine, utils.getdepthCounts(tid) + 1) + "▼" + "(" + joinPoint.getSignature().getDeclaringType() + ") ");
        printTo(type2PrintTid(tid) + ASPECT_LOG_SEQUENCE_TYPE, utils.repeat(verticalLine, utils.getdepthCounts(tid) + 1) + "┏ " + currentMethodName);


        dummyDepthText.add(currentClassName);
    }

    @NonNull
    public static String type2PrintTid(int tid) {
        return tid + ": p-";
    }

    @NonNull
    public static String type2PrintTidForUml(int tid) {
        return "";
    }


    @Around("pointcutDebugTraceAround()")
    public Object weaveDebugTraceAround(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        String currentClassName = utils.getOnlyClassName(methodSignature.getDeclaringType().getSimpleName());
        String currentMethodName = utils.getOnlyClassName(methodSignature.getName());

        int tid = android.os.Process.myTid();
        List<String> dummyDepthText = utils.getdepthTexts(tid);

        depthCounts.put(tid, utils.getdepthCounts(tid) + 1);

        LogPrinter.logAroundBefore(currentClassName, tid);

        if (argAll.length() > 0) {
            printTo(type2PrintTid(tid) + ASPECT_LOG_SEQUENCE_TYPE,  utils.repeat(verticalLine, utils.getdepthCounts(tid) + 1) + argAll);
        }

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Object result = joinPoint.proceed();
        stopWatch.stop();

        LogPrinter.logAroundAfter(currentClassName, currentMethodName, dummyDepthText, stopWatch.getTotalTimeMillis(), tid, result);


        printTo(type2PrintTid(tid) + ASPECT_LOG_SEQUENCE_TYPE,  utils.repeat(verticalLine, utils.getdepthCounts(tid) + 1) + "result  " + result );
        printTo(type2PrintTid(tid) + ASPECT_LOG_SEQUENCE_TYPE,  utils.repeat(verticalLine, (utils.getdepthCounts(tid))) +
                "┗ " + currentMethodName +
                " : " + stopWatch.getTotalTimeMillis() + "ms");

        //after process
        depthCounts.put(tid, utils.getdepthCounts(tid) - 1);

        if(dummyDepthText.size() >0) {
            dummyDepthText.remove(dummyDepthText.size()-1);
        }
        depthTexts.put(tid, dummyDepthText);

        return result;
    }

/*    @Around("(execution(* android.app.Activity.onResume(..)) || execution(* android.app.Activity.onCreate(..)) || execution(* show(..)) )")
    public Object postonResume(ProceedingJoinPoint joinPoint) throws Throwable {
      final Activity activity = (Activity) joinPoint.getTarget();
        final View rootView = activity.getWindow().getDecorView().getRootView();


        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                storeScreenshot(takescreenshotOfRootView(rootView), Calendar.getInstance().getTimeInMillis() + "-" + activity.getLocalClassName()  );
            }
        }, 500);
        Object result = joinPoint.proceed();
        return result;
    }*/


    public static Bitmap takescreenshot(View v) {
        v.setDrawingCacheEnabled(true);
        v.buildDrawingCache(true);
        Bitmap b = Bitmap.createBitmap(v.getDrawingCache(true));
        v.setDrawingCacheEnabled(false);
        return b;
    }

    public static Bitmap takescreenshotOfRootView(View v) {
        return takescreenshot(v.getRootView());
    }
    public void storeScreenshot(Bitmap bitmap, String filename) {
        String path = Environment.getExternalStorageDirectory().toString() + "/" + filename + ".jpg";
        Log.e("path", path);
        OutputStream out = null;
        File imageFile = new File(path);

        try {
            out = new FileOutputStream(imageFile);
            // choose JPEG format
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out);
            out.flush();
        } catch (FileNotFoundException e) {
            Log.e("FileNotFoundException", e.getMessage());
            // manage exception ...
        } catch (IOException e) {
            Log.e("IOException", e.getMessage());
            // manage exception ...
        } finally {

            try {
                if (out != null) {
                    out.close();
                }

            } catch (Exception exc) {
            }

        }
    }
    /**
     * Class representing a StopWatch for measuring time.
     */
    public class StopWatch {
        private long startTime;
        private long endTime;
        private long elapsedTime;

        public StopWatch() {
            //empty
        }

        private void reset() {
            startTime = 0;
            endTime = 0;
            elapsedTime = 0;
        }

        public void start() {
            reset();
            startTime = System.nanoTime();
        }

        public void stop() {
            if (startTime != 0) {
                endTime = System.nanoTime();
                elapsedTime = endTime - startTime;
            } else {
                reset();
            }
        }

        public long getTotalTimeMillis() {
            return (elapsedTime != 0) ? TimeUnit.NANOSECONDS.toMillis(endTime - startTime) : 0;
        }
    }

    public static class LogPrinter {


        private static void logBefore(String className, String methodName, String lastClassMethod, int  tid ) {


            //hidden for UML
            printTo(ASPECT_LOG_PLANTUML_TYPE,   type2PrintTidForUml(tid) + lastClassMethod + umlWithColorBlock(tid) + "->" + type2PrintTidForUml(tid) + className + " : " + methodName  + "#" + tid );

        }

        public static void logBeforeArg(String className,  int  tid, String args) {
//            note right of Alice
//            Alice の<u:blue>右</u>側に
//                    <back:cadetblue>表示</back>するノート
//            end note
            if(args.isEmpty()) return;
            printTo(ASPECT_LOG_PLANTUML_TYPE,   "note right of " +  type2PrintTidForUml(tid) + className   + "\n " + args + "\n end note"  );

        }

        public static void logAfterResult(String className,  int  tid, Object result) {
//            note right of Alice
//            Alice の<u:blue>右</u>側に
//                    <back:cadetblue>表示</back>するノート
//            end note
            if(result == null) return;
            printTo(ASPECT_LOG_PLANTUML_TYPE,   "note right of " +  type2PrintTidForUml(tid) + className   + "\n result :" + result + "\n end note"  );

        }


        private static int getProcessColor(int tid) {
            if(!processColor.containsKey(tid)) {
                Random r = new Random();
                processColor.put(tid, r.nextInt(0xffffff + 1));
            }
            return processColor.get(tid);
        }

        private static String umlWithColor(int tid) {
            return String.format("#%06x", getProcessColor(tid));
        }

        private static String umlWithColorBlock(int tid) {
            return "[" + umlWithColor(tid) + "]";
        }

        private static void logAroundBefore(String className, int tid) {
            //hidden for UML
            printTo(ASPECT_LOG_PLANTUML_TYPE, ACTIVATE + type2PrintTidForUml(tid) + className + " " + umlWithColor(tid));

        }

        private static void logAroundAfter(String className, String methodName, List<String> depthText, long estimate, int  tid, Object result ) {
            String returnClassName = START;
            if(depthText.size() > 1) {
                returnClassName = depthText.get(depthText.size()-2);
            }
            //hidden for UML
//new but not goof   relation 420,423           printTo(ASPECT_LOG_PLANTUML_TYPE, "return " +type2PrintTidForUml(tid) + methodName + "  (" + estimate + "ms)"); //### weaveDebugTraceBefore:


            long nativeMax = Debug.getNativeHeapSize() / 1024;
            long nativeAllocated = Debug.getNativeHeapAllocatedSize() / 1024;
            long nativeFree = Debug.getNativeHeapFreeSize() / 1024;

            printTo(ASPECT_LOG_PLANTUML_TYPE, type2PrintTidForUml(tid) + className + umlWithColorBlock(tid)
                    + "-->" + type2PrintTidForUml(tid) + returnClassName + " : " + methodName + "#" + tid  + "  (" + estimate + "ms," + nativeMax + "MB," + nativeAllocated + "MB," + nativeFree + "MB)"); //### weaveDebugTraceBefore:
            LogPrinter.logAfterResult(className,  tid,  result);
            //hidden for UML
            printTo(ASPECT_LOG_PLANTUML_TYPE, DEACTIVATE + type2PrintTidForUml(tid) + className + " " +  umlWithColor(tid) );

        }
    }

    public static class utils {
        public synchronized static String repeat(String string, int times) {
            StringBuilder out = new StringBuilder();

            while (times-- > 0) {
                out.append(string);
            }
            return out.toString();
        }


        private static String getOnlyClassName(String classname) {
            if (classname.indexOf("$") < 1) {
                return isEmptyName(classname);
            }
            String[] str = classname.split("\\\\$", 0);
            return isEmptyName(str[0]);
        }

        private static String getLastClassMethod(String lastClassMethod, List<String> dummyDepthText, int logType) {
            if(dummyDepthText.size() > 0) {
                return isEmptyName(dummyDepthText.get(dummyDepthText.size()-logType));
            }
            return lastClassMethod;
        }

        private static List<String> getdepthTexts(int tid) {
            if(!depthTexts.containsKey(tid)) {
                depthTexts.put(tid, new ArrayList<String>());
            }
            return depthTexts.get(tid);
        }

        private static int getdepthCounts(int tid) {
            if(!depthCounts.containsKey(tid)) {
                depthCounts.put(tid, 0);
            }
            return depthCounts.get(tid);
        }

        private static String isEmptyName(String methodName) {
            if (methodName.length() < 2) {
                return "NULL";
            }
            return methodName;
        }

        private static String getArgs(JoinPoint joinPoint, MethodSignature methodSignature) {
            Object[] objArray = joinPoint.getArgs();
            String[] sigParamNames = methodSignature.getParameterNames();
            int i = 0;
            String argName = "";
            StringBuilder argAll = new StringBuilder();
            for (Object obj : objArray) {
                if (obj == null) continue;
                argName = sigParamNames[i];
                i++;
                argAll.append(argName + ":[" + obj.toString() + "] , ");
            }

            if (argAll.length() > 1) {
                argAll.insert(0, "args  ");
            }
            return argAll.toString();
        }
    }



    public static void printTo(String category, String message) {
        if (!category.equals(ASPECT_LOG_PLANTUML_TYPE)) return;
        Log.d(category, message );
        //--to db


        if(logBufferCount < logBufferStoreCountLimit) {
            logBufferCount++;
            logBufferMessages += message + "\\n";
            return;
        }
        try {
            if (OptimalAgentApplication.getInstance() == null) {
                return;
            }
        }catch (Exception e) {
            return;
        }

        if (OptimalAgentApplication.TestLogController.logHelper != null) {
            OptimalAgentApplication.TestLogController.insert(category + " // " + message);
        }

        logBufferCount = 1;
        logBufferMessages = "";
    }



}