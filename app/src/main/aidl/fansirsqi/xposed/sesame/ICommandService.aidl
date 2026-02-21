package fansirsqi.xposed.sesame;

import fansirsqi.xposed.sesame.ICallback;
import fansirsqi.xposed.sesame.IStatusListener;

interface ICommandService {
    void executeCommand(String command, ICallback callback);
    void registerListener(IStatusListener listener);
    void unregisterListener(IStatusListener listener);
}
