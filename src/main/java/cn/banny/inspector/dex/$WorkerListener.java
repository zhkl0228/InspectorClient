package cn.banny.inspector.dex;

/**
 * default worker listener implementation
 * Created by zhkl0228 on 2018/3/24.
 */
class $WorkerListener implements WorkerListener {
    private final int total;
    private int current;

    $WorkerListener(int total) {
        this.total = total;
    }

    @Override
    public void notifyBegin(String msg) {
        if (current++ < total) {
            int percent = (current * 100) / total;
            String p = (percent < 10) ? (" " + percent + '%') : (percent < 100) ? ("" + percent + '%') : ("" + percent);
            System.out.println("[" + p + "]" + msg);
        }
    }

    @Override
    public void notifyException(Exception e) {
        e.printStackTrace();
    }
}
