package fansirsqi.xposed.sesame.hook.rpc.bridge;

import fansirsqi.xposed.sesame.hook.Toast;
import fansirsqi.xposed.sesame.util.*;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XposedHelpers;
import fansirsqi.xposed.sesame.data.General;
import fansirsqi.xposed.sesame.entity.RpcEntity;
import fansirsqi.xposed.sesame.hook.ApplicationHook;
import fansirsqi.xposed.sesame.hook.rpc.intervallimit.RpcIntervalLimit;
import fansirsqi.xposed.sesame.model.BaseModel;

/**
 * 新版rpc接口，支持最低目标应用版本v10.3.96.8100 记录rpc抓包，支持最低目标应用版本v10.3.96.8100
 */
public class NewRpcBridge implements RpcBridge {
    private static final String TAG = NewRpcBridge.class.getSimpleName();
    private static final long ALIPAY_START_DEBOUNCE_TIME = 8000L; // 目标应用启动防抖时间：8秒
    private static volatile long lastAlipayStartTime = 0L; // 上次启动目标应用的时间戳
    private static final Object alipayStartLock = new Object(); // 目标应用启动锁
    private ClassLoader loader;
    private Object newRpcInstance;
    private Method parseObjectMethod;
    private Class<?>[] bridgeCallbackClazzArray;
    private Method newRpcCallMethod;
    private final AtomicInteger maxErrorCount = new AtomicInteger(0);
    private final Integer setMaxErrorCount = BaseModel.Companion.getSetMaxErrorCount().getValue();

    ArrayList<String> errorMark = new ArrayList<>(Arrays.asList(
            "1004", "1009", "2000", "46", "48"
    ));
    ArrayList<String> errorStringMark = new ArrayList<>(Arrays.asList(
            "繁忙", "拒绝", "网络不可用", "重试"
    ));

    // 需要屏蔽错误日志的RPC方法列表
    ArrayList<String> silentErrorMethods = new ArrayList<>(List.of(
            "com.alipay.adexchange.ad.facade.xlightPlugin",  //木兰集市 第一次
            "alipay.antforest.forest.h5.takeLook"  //找能量
    ));

    /**
     * 检查指定的RPC方法是否应该显示错误日志
     *
     * @param methodName RPC方法名称
     * @return 如果应该显示错误日志返回true，否则返回false
     */
    private boolean shouldShowErrorLog(String methodName) {
        return methodName != null && !silentErrorMethods.contains(methodName);
    }

    /**
     * 记录RPC请求返回null的原因
     *
     * @param rpcEntity RPC请求实体
     * @param reason    返回null的原因
     * @param count     当前重试次数
     */
    private void logNullResponse(RpcEntity rpcEntity, String reason, int count) {
        String methodName = rpcEntity != null ? rpcEntity.getRequestMethod() : "unknown";
        if (shouldShowErrorLog(methodName)) {
            Log.error(TAG, "RPC返回null | 方法: " + methodName + " | 原因: " + reason + " | 重试: " + count);
        }
    }

    @Override
    public RpcVersion getVersion() {
        return RpcVersion.NEW;
    }

    @Override
    public void load() throws Exception {
        loader = ApplicationHook.classLoader;
        try {
            Object service = XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.alipay.mobile.nebulacore.Nebula", loader), "getService");
            Object extensionManager = XposedHelpers.callMethod(service, "getExtensionManager");
            Method getExtensionByName = extensionManager.getClass().getDeclaredMethod("createExtensionInstance", Class.class);
            getExtensionByName.setAccessible(true);
            newRpcInstance = getExtensionByName.invoke(null, loader.loadClass("com.alibaba.ariver.commonability.network.rpc.RpcBridgeExtension"));
            if (newRpcInstance == null) {
                Object nodeExtensionMap = XposedHelpers.callMethod(extensionManager, "getNodeExtensionMap");
                if (nodeExtensionMap != null) {
                    @SuppressWarnings("unchecked")
                    Map<Object, Map<String, Object>> map = (Map<Object, Map<String, Object>>) nodeExtensionMap;
                    for (Map.Entry<Object, Map<String, Object>> entry : map.entrySet()) {
                        Map<String, Object> map1 = entry.getValue();
                        for (Map.Entry<String, Object> entry1 : map1.entrySet()) {
                            if ("com.alibaba.ariver.commonability.network.rpc.RpcBridgeExtension".equals(entry1.getKey())) {
                                newRpcInstance = entry1.getValue();
                                break;
                            }
                        }
                    }
                }
                if (newRpcInstance == null) {
                    Log.record(TAG, "get newRpcInstance null");
                    throw new RuntimeException("get newRpcInstance is null");
                }
            }
            parseObjectMethod = loader.loadClass("com.alibaba.fastjson.JSON").getMethod("parseObject", String.class);
            Class<?> bridgeCallbackClazz = loader.loadClass("com.alibaba.ariver.engine.api.bridge.extension.BridgeCallback");
            bridgeCallbackClazzArray = new Class[]{bridgeCallbackClazz};
            newRpcCallMethod = newRpcInstance.getClass().getMethod("rpc"
                    , String.class
                    , boolean.class
                    , boolean.class
                    , String.class
                    , loader.loadClass(General.JSON_OBJECT_NAME)
                    , String.class
                    , loader.loadClass(General.JSON_OBJECT_NAME)
                    , boolean.class
                    , boolean.class
                    , int.class
                    , boolean.class
                    , String.class
                    , loader.loadClass("com.alibaba.ariver.app.api.App")
                    , loader.loadClass("com.alibaba.ariver.app.api.Page")
                    , loader.loadClass("com.alibaba.ariver.engine.api.bridge.model.ApiContext")
                    , bridgeCallbackClazz
            );
            Log.record(TAG, "get newRpcCallMethod successfully");
        } catch (Exception e) {
            Log.record(TAG, "get newRpcCallMethod err:");
            throw e;
        }
    }

    @Override
    public void unload() {
        newRpcCallMethod = null;
        bridgeCallbackClazzArray = null;
        parseObjectMethod = null;
        newRpcInstance = null;
        loader = null;
    }

    /**
     * 发送RPC请求并获取响应字符串
     * <p>
     * 该方法是requestObject的包装，将RPC响应对象转换为字符串返回：
     * 1. 调用requestObject执行实际的RPC请求
     * 2. 从返回的RPC实体中提取响应字符串
     * </p>
     *
     * @param rpcEntity RPC请求实体，包含请求方法、参数等信息
     * @param tryCount 最大尝试次数，设置为1表示只尝试一次不重试，设置为0表示不尝试，大于1表示有重试
     * @param retryInterval 重试间隔（毫秒），负值表示使用默认延迟，0表示立即重试
     * @return 响应字符串，如果请求失败则返回null
     */
    public String requestString(RpcEntity rpcEntity, int tryCount, int retryInterval) {
        RpcEntity resRpcEntity = requestObject(rpcEntity, tryCount, retryInterval);
        if (resRpcEntity != null) {
            return resRpcEntity.getResponseString();
        }
        return null;
    }

    /**
     * 发送RPC请求并获取响应对象
     * <p>
     * 该方法负责执行实际的RPC调用，支持重试机制和错误处理：
     * 1. 根据tryCount参数控制重试次数
     * 2. 根据retryInterval参数控制重试间隔
     *    - retryInterval < 0: 使用600ms+随机延迟
     *    - retryInterval = 0: 不等待立即重试
     *    - retryInterval > 0: 使用指定的毫秒数等待
     * 3. 检测网络错误并根据配置进入离线模式或尝试重新登录
     * </p>
     *
     * @param rpcEntity RPC请求实体，包含请求方法、参数等信息
     * @param tryCount 最大尝试次数，设置为1表示只尝试一次不重试，设置为0表示不尝试，大于1表示有重试
     * @param retryInterval 重试间隔（毫秒），负值表示使用默认延迟，0表示立即重试
     * @return 包含响应数据的RPC实体，如果请求失败则返回null
     */
    @Override
    public RpcEntity requestObject(RpcEntity rpcEntity, int tryCount, int retryInterval) {
        // 方法开始时，将成员变量赋值给局部变量，以避免在方法执行期间因其他线程的unload()调用而导致成员变量变为null
        Method localNewRpcCallMethod = newRpcCallMethod;
        Method localParseObjectMethod = parseObjectMethod;
        Object localNewRpcInstance = newRpcInstance;
        ClassLoader localLoader = loader;
        Class<?>[] localBridgeCallbackClazzArray = bridgeCallbackClazzArray;

        if (ApplicationHook.offline) {
            return null;
        }

        // 如果RPC组件未准备好，尝试重新初始化一次
        if (localNewRpcCallMethod == null) {
             Log.record(TAG, "RPC方法为null，尝试重新初始化...");
            try {
                load();
                // 重新加载初始化后的变量
                localNewRpcCallMethod = newRpcCallMethod;
                localParseObjectMethod = parseObjectMethod;
                localNewRpcInstance = newRpcInstance;
                localLoader = loader;
                localBridgeCallbackClazzArray = bridgeCallbackClazzArray;
                 Log.record(TAG, "RPC重新初始化成功");
            } catch (Exception e) {
                Log.error(TAG, "RPC重新初始化失败:");
                Log.printStackTrace(e);
                logNullResponse(rpcEntity, "RPC组件初始化失败", 0);
                return null;
            }
        }

        if (localNewRpcCallMethod == null || localParseObjectMethod == null
                || localNewRpcInstance == null || localLoader == null || localBridgeCallbackClazzArray == null) {
            logNullResponse(rpcEntity, "RPC组件不完整", 0);
            return null;
        }
        try {
            int count = 0;
            do {
                count++;
                try {
                    RpcIntervalLimit.INSTANCE.enterIntervalLimit(Objects.requireNonNull(rpcEntity.getRequestMethod()));
                    Class<?>[] finalLocalBridgeCallbackClazzArray = localBridgeCallbackClazzArray;
                    localNewRpcCallMethod.invoke(
                            localNewRpcInstance, rpcEntity.getRequestMethod(), false, false, "json", localParseObjectMethod.invoke(null,
                                    rpcEntity.getRpcFullRequestData()), "", null, true, false, 0, false, "", null, null, null, Proxy.newProxyInstance(localLoader,
                                    localBridgeCallbackClazzArray, (proxy, innerMethod, args) -> {
                                        if ("equals".equals(innerMethod.getName())) {
                                            return proxy == args[0];
                                        }
                                        if ("hashCode".equals(innerMethod.getName())) {
                                            return System.identityHashCode(proxy);
                                        }
                                        if ("toString".equals(innerMethod.getName())) {
                                            return "Proxy for " + finalLocalBridgeCallbackClazzArray[0].getName();
                                        }
                                        if (args != null && args.length >= 1 && "sendJSONResponse".equals(innerMethod.getName())) {
                                            try {
                                                Object obj = args[0];
                                                // 获取 JSON 字符串，失败时重试一次
                                                String jsonString = null;
                                                try {
                                                    jsonString = (String) XposedHelpers.callMethod(obj, "toJSONString");
                                                } catch (Exception e) {
                                                    // 第一次失败，尝试重试
                                                    try {
                                                        GlobalThreadPools.sleepCompat(100L);
                                                        jsonString = (String) XposedHelpers.callMethod(obj, "toJSONString");
                                                    } catch (Exception retryException) {
                                                        // 重试后仍失败，记录日志并标记错误，触发外层RPC重试
                                                        Log.record(TAG, "toJSONString 重试后仍然失败，将触发整个 RPC 请求重试: " + retryException.getMessage());
                                                        rpcEntity.setResponseObject(obj, null);
                                                        rpcEntity.setError();
                                                        return null;
                                                    }
                                                }

                                                rpcEntity.setResponseObject(obj, jsonString);
                                                if (!(Boolean) XposedHelpers.callMethod(obj, "containsKey", "success")
                                                        && !(Boolean) XposedHelpers.callMethod(obj, "containsKey", "isSuccess")) {
                                                    rpcEntity.setError();
                                                    if (shouldShowErrorLog(rpcEntity.getRequestMethod())) {
                                                        Log.error(TAG, "new rpc response1 | id: " + rpcEntity.hashCode() + " | method: " + rpcEntity.getRequestMethod() + "\n " +
                                                                "args: " + rpcEntity.getRequestData() + " |\n data: " + rpcEntity.getResponseString());
                                                    }
                                                }
                                            } catch (Exception e) {
                                                rpcEntity.setError();
                                                Log.printStackTrace(TAG,"new rpc response2 | id: " + rpcEntity.hashCode() + " | method: " + rpcEntity.getRequestMethod() +
                                                        " err:",e);
                                            }
                                        }
                                        return null;
                                    })
                    );
                    if (!rpcEntity.getHasResult()) {
                        logNullResponse(rpcEntity, "无响应结果", count);
                        return null;
                    }
                    if (!rpcEntity.getHasError()) {
                        return rpcEntity;
                    }
                    try {
                        String errorCode = (String) XposedHelpers.callMethod(rpcEntity.getResponseObject(), "getString", "error");
                        String errorMessage = (String) XposedHelpers.callMethod(rpcEntity.getResponseObject(), "getString", "errorMessage");
                        String response = rpcEntity.getResponseString();
                        String methodName = rpcEntity.getRequestMethod();

                        // 检测安全验证错误，提示手动处理（带防抖）

                        if (errorMessage != null && errorMessage.contains("为了保障您的操作安全，请进行验证后继续")) {
                            long currentTime = System.currentTimeMillis();
                            long timeSinceLastStart = currentTime - lastAlipayStartTime;
                            if (timeSinceLastStart < ALIPAY_START_DEBOUNCE_TIME) {
                                 Log.record(TAG, "距离上次提醒仅 " + timeSinceLastStart + "ms，跳过本次提醒");
                            } else {
                                synchronized (alipayStartLock) {
                                    // 双重检查，防止多线程竞争
                                    currentTime = System.currentTimeMillis();
                                    timeSinceLastStart = currentTime - lastAlipayStartTime;
                                    if (timeSinceLastStart < ALIPAY_START_DEBOUNCE_TIME) {
                                         Log.record(TAG, "距离上次提醒仅 " + timeSinceLastStart + "ms，跳过本次提醒（双重检查）");
                                    } else {
                                        lastAlipayStartTime = currentTime;
                                         Log.record(TAG, "检测到安全验证错误，请手动打开目标应用完成验证后继续");
                                        Toast.INSTANCE.show(
                                                "为了保障您的操作安全，请打开支付宝完成验证后继续"
                                        );
                                    }
                                }
                            }
                            return null;
                        }

                        if (errorMark.contains(errorCode) || errorStringMark.contains(errorMessage)) {
                            int currentErrorCount = maxErrorCount.incrementAndGet();
                            if (!ApplicationHook.offline) {
                                if (currentErrorCount > setMaxErrorCount) {
                                    ApplicationHook.setOffline(true);
                                    Notify.updateStatusText("网络连接异常，已进入离线模式");
                                    if (BaseModel.Companion.getErrNotify().getValue()) {
                                        Notify.sendNewNotification(TimeUtil.getTimeStr() + " | 网络异常次数超过阈值[" + setMaxErrorCount + "]", response);
                                    }
                                }
//                                if (BaseModel.Companion.getErrNotify().getValue()) {
//                                    Notify.sendNewNotification(TimeUtil.getTimeStr() + " | 网络异常: " + methodName, response);
//                                }//做得多错的多，不做就不会错
                                if (BaseModel.Companion.getTimeoutRestart().getValue()) {
                                    Log.record(TAG, "尝试重新登录");
                                    ApplicationHook.reLoginByBroadcast();
                                }
                            }
                            logNullResponse(rpcEntity, "网络错误: " + errorCode + "/" + errorMessage, count);
                            return null;
                        }
                        return rpcEntity;
                    } catch (Exception e) {
                        Log.error(TAG, "new rpc response | id: " + rpcEntity.hashCode() + " | method: " + rpcEntity.getRequestMethod() + " get err:");
                        Log.printStackTrace(e);
                    }
                    if (retryInterval < 0) {
                        CoroutineUtils.sleepCompat(600 + RandomUtil.delay());
                    } else if (retryInterval > 0) {
                        CoroutineUtils.sleepCompat(retryInterval);
                    }
                } catch (Throwable t) {
                    Log.error(TAG, "new rpc request | id: " + rpcEntity.hashCode() + " | method: " + rpcEntity.getRequestMethod() + " err:");
                    Log.printStackTrace(t);
                    if (retryInterval < 0) {
                        CoroutineUtils.sleepCompat(600 + RandomUtil.delay());
                    } else if (retryInterval > 0) {
                        CoroutineUtils.sleepCompat(retryInterval);
                    }
                }
            } while (count < tryCount);
            logNullResponse(rpcEntity, "重试次数耗尽", tryCount);
            return null;
        } finally {
         //   Log.record(TAG, "New RPC\n方法: " + rpcEntity.getRequestMethod() + "\n参数: " + rpcEntity.getRequestData() + "\n数据: " + rpcEntity.getResponseString() + "\n" + "\n" + "堆栈:" + new Exception().getStackTrace()[1].toString());
         //   Log.printStack(TAG);

        }
    }
}
