public class ThreadsBasics {

    public static void main(String[] args) throws InterruptedException {
        Thread platformThread = new Thread(() ->
                System.out.println("Platform thread running: " + Thread.currentThread()));
        platformThread.start();
        platformThread.join();

        Thread virtualThread = Thread.ofVirtual().start(() ->
                System.out.println("Virtual thread running: " + Thread.currentThread()));
        virtualThread.join();
    }
}
