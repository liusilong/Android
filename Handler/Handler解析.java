//Handler  以下Looper默认都是主线程中的Looper
/*
我们首先看看APP的启动类：ActivityThread中的main方法中的主要代码.
*/
public static void main(String[] args) {
	...
		//获取消息循环器
	Looper.prepareMainLooper();

	ActivityThread thread = new ActivityThread();
	//表示不是系统应用
	thread.attach(false);
	//获取系统的Handler mH
	if (sMainThreadHandler == null) {
		sMainThreadHandler = thread.getHandler();
	}

	if (false) {
		Looper.myLooper().setMessageLogging(new
			LogPrinter(Log.DEBUG, "ActivityThread"));
	}

	// End of event ActivityThreadMain.
	Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
	//开始消息循环
	Looper.loop();
	...
}
//Looper
/*
首先来看看Looper的构造方法
quitAllowed：
false：表示此消息队列不能退出，主线程的消息队列默认是不能退出的
true：表示此消息队列可以对出
*/
private Looper(boolean quitAllowed) {
	//实例化MessageQueue对象
	mQueue = new MessageQueue(quitAllowed);
	//获取当前线程
	mThread = Thread.currentThread();
}
/*
接下来我们看看Looper中的prepareMainLooper
*/
public static void prepareMainLooper() {
	//实例化当前线程的消息循环器，并且将Looper对象放入ThreadLocal中，ThreadLocal后面说
	// false代表这个消息循环器代表主线程的消息循环器是不允许退出的
	//否则会IllegalStateException("Main thread not allowed to quit.");
	prepare(false);
	synchronized (Looper.class) {
		if (sMainLooper != null) {
			throw new IllegalStateException("The main Looper has already been prepared.");
		}
		//从ThreadLocal中获取当前线程的Looper
		sMainLooper = myLooper();
	}
}
/*
prepare
*/
private static void prepare(boolean quitAllowed) {
	if (sThreadLocal.get() != null) {
		throw new RuntimeException("Only one Looper may be created per thread");
	}
	//new一个Looper放入到ThreadLocal中
	sThreadLocal.set(new Looper(quitAllowed));
}
	   
/*
myLooper 
*/
public static @Nullable Looper myLooper() {
	//从ThreadLoacal中获取当前线程的Looper对象
	return sThreadLocal.get();
}	
/*
Loop方法主要是从消息队列MessageQueue中取出消息，然后交给Handler处理
*/
public static void loop() {
	//获取当前线程的Looper
	final Looper me = myLooper();
	if (me == null) {
		throw new RuntimeException("No Looper; Looper.prepare() wasn't called on this thread.");
	}
	//获取消息队列MessageQueue
	final MessageQueue queue = me.mQueue;

	// Make sure the identity of this thread is that of the local process,
	// and keep track of what that identity token actually is.
	Binder.clearCallingIdentity();
	final long ident = Binder.clearCallingIdentity();
	//这里是一个死循环，不断的从消息队列中取出消息并交给Handler处理
	for (;;) {
		Message msg = queue.next(); // might block
		if (msg == null) {
			// No message indicates that the message queue is quitting.
			return;
		}

		// This must be in a local variable, in case a UI event sets the logger
		final Printer logging = me.mLogging;
		if (logging != null) {
			logging.println(">>>>> Dispatching to " + msg.target + " " +
				msg.callback + ": " + msg.what);
		}

		final long traceTag = me.mTraceTag;
		if (traceTag != 0 && Trace.isTagEnabled(traceTag)) {
			Trace.traceBegin(traceTag, msg.target.getTraceName(msg));
		}
		try {
			//msg.target就是Handler对象，这里是消息分发给Handler的dispatchMessage方法处理
			msg.target.dispatchMessage(msg);
		} finally {
			if (traceTag != 0) {
				Trace.traceEnd(traceTag);
			}
		}

		if (logging != null) {
			logging.println("<<<<< Finished to " + msg.target + " " + msg.callback);
		}

		// Make sure that during the course of dispatching the
		// identity of the thread wasn't corrupted.
		final long newIdent = Binder.clearCallingIdentity();
		if (ident != newIdent) {
			Log.wtf(TAG, "Thread identity changed from 0x"
				+ Long.toHexString(ident) + " to 0x"
					+ Long.toHexString(newIdent) + " while dispatching to "
						+ msg.target.getClass().getName() + " "
							+ msg.callback + " what=" + msg.what);
		}
		//回收消息，下面会说
		msg.recycleUnchecked();
	}
}
/*
我们平时自定义的Handler发送消息的流程都是
Handler handler = new Handler(){
public void handlerMessage(Message msg){
//doSomething...
}
};
			
我们来看看Handler的构造方法，我们调用的空的构造方法最终会调用
两个参数的构造方法
*/
public Handler(Callback callback, boolean async) {
	if (FIND_POTENTIAL_LEAKS) {
		final Class<? extends Handler> klass = getClass();
		if ((klass.isAnonymousClass() || klass.isMemberClass() || klass.isLocalClass()) &&
		(klass.getModifiers() & Modifier.STATIC) == 0) {
			Log.w(TAG, "The following Handler class should be static or leaks might occur: " +
				klass.getCanonicalName());
		}
	}
	//此时的Looper是获取当前线程的Looper对象，这也是在子线程中使用Handler发送消息的时候
	//首先要调用Looper.prepare()的原因，因为只有调用的prepare方法才会初始化当前线程的Looper对象
	//在子线程中使用Handler的步骤通常是
	//1.Looper.prepare();
	//2.mHandler = new Handler(){handlerMessage(){}};
	//3.Looper.loop();
	mLooper = Looper.myLooper();
	if (mLooper == null) {
		throw new RuntimeException(
			"Can't create handler inside thread that has not called Looper.prepare()");
	}
	//获取当前线程的消息队列，此消息队列在Looper的构造方法中初始化
	mQueue = mLooper.mQueue;
	mCallback = callback;
	mAsynchronous = async;
}
/*
然后我们会使用handler去发送消息：handler.sendEmptyMessage(0);
看看Handler中的sendEmptyMessage方法
*/
public final boolean sendEmptyMessage(int what)
{
	return sendEmptyMessageDelayed(what, 0);
}
/*
会调用sendEmptyMessageDelayed方法
*/
public final boolean sendEmptyMessageDelayed(int what, long delayMillis) {
	//从这里可以看出即使我们平时发送的空消息，最终还是实例化一个Message对象
	//这里的msg是从消息池里面取出来的（复用），这个后面
	Message msg = Message.obtain();
	msg.what = what;
	return sendMessageDelayed(msg, delayMillis);
}
public final boolean sendMessageDelayed(Message msg, long delayMillis)
{
	if (delayMillis < 0) {
		delayMillis = 0;
	}
	return sendMessageAtTime(msg, SystemClock.uptimeMillis() + delayMillis);
}
public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
	MessageQueue queue = mQueue;
	if (queue == null) {
		RuntimeException e = new RuntimeException(
			this + " sendMessageAtTime() called with no mQueue");
		Log.w("Looper", e.getMessage(), e);
		return false;
	}
	return enqueueMessage(queue, msg, uptimeMillis);
}
private boolean enqueueMessage(MessageQueue queue, Message msg, long uptimeMillis) {
	msg.target = this;
	if (mAsynchronous) {
		msg.setAsynchronous(true);
	}
	//调用MessageQueue的enqueueMessage方法将消息入队
	return queue.enqueueMessage(msg, uptimeMillis);
}
/*
enqueueMessage 消息入队
消息入队是根据Message的when参数来改变当前Message链表
也就是说消息入队的时候可以插队
*/
boolean enqueueMessage(Message msg, long when) {
	if (msg.target == null) {
		throw new IllegalArgumentException("Message must have a target.");
	}
	if (msg.isInUse()) {
		throw new IllegalStateException(msg + " This message is already in use.");
	}

	synchronized (this) {
		if (mQuitting) {
			IllegalStateException e = new IllegalStateException(
				msg.target + " sending message to a Handler on a dead thread");
			Log.w(TAG, e.getMessage(), e);
			msg.recycle();
			return false;
		}

		msg.markInUse();
		msg.when = when;
		Message p = mMessages;
		boolean needWake;
		if (p == null || when == 0 || when < p.when) {
			// New head, wake up the event queue if blocked.
			msg.next = p;
			mMessages = msg;
			needWake = mBlocked;
		} else {
			// Inserted within the middle of the queue.  Usually we don't have to wake
			// up the event queue unless there is a barrier at the head of the queue
			// and the message is the earliest asynchronous message in the queue.
			needWake = mBlocked && p.target == null && msg.isAsynchronous();
			Message prev;
			for (;;) {
				prev = p;
				p = p.next;
				if (p == null || when < p.when) {
					break;
				}
				if (needWake && p.isAsynchronous()) {
					needWake = false;
				}
			}
			msg.next = p; // invariant: p == prev.next
			prev.next = msg;
		}

		// We can assume mPtr != 0 because mQuitting is false.
		if (needWake) {
			nativeWake(mPtr);
		}
	}
	return true;
}
/*
消息入队之后，然后当前线程的Looper就会不断的从MessageQueue里面取出消息
之前我们看到Looper#loop就是一个死循环，然后里面会有一句分发消息的
msg.target.dispatchMessage(msg);
然后我们在Message对象中看到 Handler target;
所以 msg.target就是一个hander对象
接着会调用Handler的dispatchMessage方法
*/
public void dispatchMessage(Message msg) {
	//判断Message的callback是否为空
	if (msg.callback != null) {
		handleCallback(msg);
	} else {
		//这个mCallback在初始化Handler的时候一般为null
		if (mCallback != null) {
			if (mCallback.handleMessage(msg)) {
				return;
			}
		}
		//执行hadnlerMessage方法
		handleMessage(msg);
	}
}
 
/*
看上面，首先会判断msg.callback是否为空,不为空则会调用 handleCallback(msg);
而从Message类中可以看到callback就是一个Runnable类型的变量Runnable callback;
所以来看看handleCallback方法。
如下：会调用Runnable中的run方法，注意此时的run方法是在主线程中调用的，
*/
private static void handleCallback(Message message) {
	message.callback.run();
}
/*
dispatchMessage方法最终会执行handlerMessage方法
*/
/**
子类必须实现此方法用于接受消息
* Subclasses must implement this to receive messages.
*/
public void handleMessage(Message msg) {
}
			
/*
接着说下ThreadLocal
首先看看set方法
每个Thread对象中都会有一个ThreadLocalMap对象
所以所用ThreadLocal存放值其实就是在当前Thread中的ThreadLocalMap存放值
这样就可以保证当前线程中的值不会影响其他线程中的值
*/
public void set(T value) {
	//获取当前的线程对象
	Thread t = Thread.currentThread();
	//获取ThreadLocalMap，一个类似于HashMap的数据结构
	ThreadLocalMap map = getMap(t);
	if (map != null)
		map.set(this, value);
	else
		createMap(t, value);
}
//获取当前线程中的ThreadLocalMap
ThreadLocalMap getMap(Thread t) {
	return t.threadLocals;
}
//初始化一个ThreadLoacalMap
void createMap(Thread t, T firstValue) {
	t.threadLocals = new ThreadLocalMap(this, firstValue);
}
/*
ThreadLocal#get
这里的get方发也是类似于HashMap里面你的get方法
*/
public T get() {
	Thread t = Thread.currentThread();
	ThreadLocalMap map = getMap(t);
	if (map != null) {
		ThreadLocalMap.Entry e = map.getEntry(this);
		if (e != null)
			return (T)e.value;
	}
	return setInitialValue();
}
					

/**
* 再来看看Message#obtain					
* Return a new Message instance from the global pool. Allows us to
* avoid allocating new objects in many cases.
*/
public static Message obtain() {
	synchronized (sPoolSync) {
		if (sPool != null) {
			//当前消息链
			Message m = sPool;
			//将m.next赋值给sPool
			sPool = m.next;
			//将m.next赋值为null，也就是将m变成一个空消息
			m.next = null;
			m.flags = 0; // clear in-use flag
			sPoolSize--;
			//返回m
			return m;
		}
	}
	return new Message();
}
/*
还有就是在Looper.loop()中当消息分发完之后回收消息 msg.recycleUnchecked
*/
void recycleUnchecked() {
	//将消息还原，就是清除消息上面的所有属性
	// Mark the message as in use while it remains in the recycled object pool.
	// Clear out all other details.
	flags = FLAG_IN_USE;
	what = 0;
	arg1 = 0;
	arg2 = 0;
	obj = null;
	replyTo = null;
	sendingUid = -1;
	when = 0;
	target = null;
	callback = null;
	data = null;

	synchronized (sPoolSync) {
		//这里给的 MAX_POOL_SIZE = 50
		if (sPoolSize < MAX_POOL_SIZE) {
			//将sPool(消息链)赋值给当前消息的next
			next = sPool;
			//将当前消息赋值给消息池
			sPool = this;
			//消息池的长度递增
			sPoolSize++;
		}
	}
}
						   
						   
						   
						  