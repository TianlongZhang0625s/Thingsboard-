# Actor 模型理解
Actor模式是一种并发模型，与另一种模型共享内存完全相反，Actor模型share nothing。所有的线程(或进程)通过消息传递的方式进行合作，这些线程(或进程)称为Actor。共享内存更适合单机多核的并发编程，而且共享带来的问题很多，编程也困难。随着多核时代和分布式系统的到来，共享模型已经不太适合并发编程，因此几十年前就已经出现的Actor模型又重新受到了人们的重视。MapReduce就是一种典型的Actor模式，而在语言级对Actor支持的编程语言Erlang又重新火了起来，Scala也提供了Actor，但是并不是在语言层面支持，Java也有第三方的Actor包，
Go语言channel机制也是一种类Actor模型。
## 单线程编程
单核单机时代一般都是单线程编程，如果把程序比作一个工厂，那么只有一个工人，这个工人负责所有的事情，所有的原料，工具产品等都放到一个地方，因为只有一个人，因此使用一套工具就行，取原料也不用排队等候。
## 多线程编程-共享内存
到了多核时代，有多个工人，这些工人共同使用一个仓库和车间，干什么都要排队。比如我要从一块钢料切出一块来用，我得等别人先用完。有个扳手，另一个人在用，我得等他用完。两个人都要用一个切割机从一块钢材切一块钢铁下来用，但是一个人拿到了钢材，一个人拿到了切割机，他们互相都不退让，结果谁都干不了活。
假如现在有一个任务，找100000以内的素数的个数，最多使用是个线程，如果用共享内存的方法，可以用下面的代码实现。可以看到，这些线程共享了currentNum和totalPrimeCount，对它们做操作时必须上锁。
```java
public class PrimeCount implements Runnable {
   
    private int currentNum = 2;  //从2开始找
    private int totalPrimeCount = 0; //当前已经找到的
    
    //取一个数，不能重复，最大到100000
    private int incrCurrentNum() { 
        synchronized (this) {     //如果不用锁，必然会出错。
            if(currentNum > 100000) {
                return -1;
            } else {
                int result = currentNum;
                currentNum++;
                return result;
            }  
        }
    }
    
   //把某个线程找到的素数个数加上
    private void accPrimeCount(int count) { 
        synchronized (this) {
            totalPrimeCount += count;
        }
    }
    
    @Override
     //一直取数并判断是否为素数，取不到了就把找到的个数累加
    public void run() { 
        int primeCount = 0;
        int num;
        while((num=incrCurrentNum()) != -1) {
            if(isPrime(num)) {
                primeCount++;
            }
        }
        accPrimeCount(primeCount);
    }
    private boolean isPrime(int num) {
        for(int i = 2; i < num; i++) {
            if(num % i == 0) {
                return false;
            }
        }
        return true;
    } 
    
    @SuppressWarnings("static-access")
    public static void main(String[] args){
        PrimeCount pc = new PrimeCount();
        for(int i = 0; i < 10; i++) {
            new Thread(pc).start();
        }
        try {
            Thread.currentThread().sleep(5000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.out.println(pc.getTotalPrimeCount());
    }
    
    public int getTotalPrimeCount() {
        return totalPrimeCount;
    }
 
}
```
## 多线程/分布式编程-Actor模型
到了分布式系统时代，工厂已经用流水线了，每个人都有明确分工，这就是Actor模式。每个线程都是一个Actor，这些Actor不共享任何内存，所有的数据都是通过消息传递的方式进行的。
如果用Actor模型实现统计素数个数，那么我们需要1个actor做原料的分发，就是提供要处理的整数，然后10个actor加工，每次从分发actor那里拿一个整数进行加工，最终把加工出来的半成品发给组装actor，组装actor把10个加工actor的结果汇总输出。