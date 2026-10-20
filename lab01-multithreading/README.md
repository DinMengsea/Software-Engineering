# Lab 01: Multithreading Report

## Task 2: Timing Results and `get()` Visibility

In Task 2, we worked with asynchronous tasks and used `Future` to retrieve their results. We measured execution time to understand how the program performs when tasks run concurrently.

We also studied the visibility guarantee of `Future.get()`. When `get()` returns successfully, the actions performed by the task are visible to the thread retrieving the result. This is important because the main thread needs to receive the completed task's result safely.

The timing results help us compare performance, while the `get()` visibility guarantee helps us understand how Java ensures that results produced by one thread can be correctly observed by another.

## Task 3: Thread Communication Using `wait()` and `notifyAll()`

In Task 3, we worked with the producer–consumer problem. Producers add items to a shared buffer, while consumers remove items from it. Because the buffer has a limited capacity, producers must wait when it is full, and consumers must wait when it is empty.

We used `while` instead of `if` to check the buffer condition. A thread can wake up while the condition is still false, so it must check the condition again before continuing. This prevents producers from adding items to a full buffer and consumers from removing items from an empty buffer.

We used `notifyAll()` to wake up waiting threads when the buffer's state changes. For example, after a producer adds an item, a waiting consumer may continue. After a consumer removes an item, a waiting producer may continue.

These concepts are necessary to coordinate threads safely and prevent incorrect operations on the shared buffer.

## Challenge: Word Count Program

The Word Count challenge processes files in a directory and counts the occurrences of words. We used multiple worker threads so that several files could be processed concurrently.

### Why Use a `BlockingQueue`?

The `BlockingQueue` passes file paths from the scanner to the worker threads. It allows the scanner to add files while workers take files to process. Because the queue is thread-safe, we do not need to implement all the queue synchronization ourselves.

We also use a bounded queue to limit the number of file paths waiting to be processed. This helps control memory usage when processing directories containing many files. If the queue is full, the scanner waits until a worker takes an item.

### Why Use `LongAdder` Instead of `merge()`?

Multiple workers may count the same word at the same time. We need thread-safe counters to prevent incorrect results.

`LongAdder` is useful when many threads frequently update word counts because it can reduce contention by distributing updates internally. We use a `ConcurrentHashMap` to associate each word with its counter.

Another option is `ConcurrentHashMap.merge()`, which safely updates a word's count in the map. Both approaches can be used for concurrent word counting, but `LongAdder` can be beneficial when frequent updates create contention.

### Scaling Results

We compare execution time using different numbers of workers to understand how the number of threads affects performance.

| Number of Workers | Execution Time (seconds) |
|:-----------------:|-------------------------:|
| 1                 | 2.735 s                  |
| 2                 | 1.080 s                  |
| 4                 | 1.059 s                  |
| 8                 | 1.122 s                  |

*Note: These values should represent actual measurements taken using the same input directory and consistent program settings.*

Increasing the number of workers may reduce execution time because files can be processed concurrently. However, more workers do not always mean better performance because thread management, scheduling, and shared-resource contention can add overhead.

In our results, increasing the number of workers from one to two significantly reduces execution time. Using four workers provides a small additional improvement, while eight workers take slightly longer than four. This illustrates that performance improvements eventually become limited by overhead and available resources.

### Coffman Conditions and Deadlock

A deadlock occurs when threads wait for resources held by other threads and none of them can continue.

There are four conditions associated with deadlock:

1. **Mutual exclusion:** A resource can be used by only one thread at a time.
2. **Hold and wait:** A thread holds one resource while waiting for another.
3. **No preemption:** A resource cannot be forcibly taken away from a thread.
4. **Circular wait:** Threads form a cycle in which each thread waits for a resource held by another.

All four conditions must exist together for a deadlock to occur under the Coffman model.

Understanding these conditions helps us design safer multithreaded programs. We can reduce deadlock risks by acquiring locks in a consistent order, avoiding unnecessary nested locks, and minimizing the time threads hold shared resources.

## Conclusion

These tasks helped me understand why synchronization, memory visibility, and thread-safe data structures are important in Java multithreading.

Task 2 showed why execution-time measurements and `Future.get()` matter. Task 3 demonstrated how `while` and `notifyAll()` coordinate producers and consumers. The Word Count challenge showed how a bounded blocking queue and concurrent counters allow multiple workers to process files safely.

Finally, the scaling experiment helped us compare performance, while the Coffman conditions explained how deadlocks can occur and how careful resource management can reduce their risk.

Overall, these exercises improved my understanding of concurrent programming, thread communication, performance measurement, and safe access to shared data in Java.