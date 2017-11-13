## Dagger2案例

### Scrope

- 通常@Scrope都设置在Component和provide的方法上
- If at least one provide method has a scope annotation the Component should have the **same scope annotation**.
- The Component can be unscoped only if all provide methods in all its modules are unscoped too（**Component可以没有范围，除非所有的modules中的所有肚饿provide method都没范围**）

### 关联组件（Link Components）

- 方法一：组件依赖（Component dependencies）
  - 两个相互依赖的组件不能有相同的Scope。如两个Component都是@Singleton范围，如果相互依赖，编译会报错：
    - **This @Singleton component cannot depend on scoped components**
  - 如果子Component中需要使用父Component中的对象，那么在父Component中显示的将这些对象声明出来声明
  - 组件(Component)可以依赖于其他组件(Component)

###案例：组件间的依赖

```java
/**Apple**/
public class Apple{
  public String getAppleInfo(){
    return "父亲手中的苹果";
  }
}
//---------------
/**Boy**/
public class Boy{
  private Apple apple;
  
  @Inject
  public Boy(Apple apple){
    this.apple = apple;
  }
  public void eat(){
    StirngBuild sb = new StirngBuild("小男孩吃了");
    sb.append(apple.getAppleInfo());
    System.out.println(sb.toString());
  }
}
//--------ParentModule-------
@Module
public class ParentModule{
  @Provides
  public Apple provideApple(){
    return new Apple();
  }
}
//--------ChildModule-------
@Moule
public class ChildModule{
  
  //这里的apple是从父组件中获取的
  @Provides
  public Boy provideBoy(Apple apple){
    return new Boy(apple);
  }
}

//--------ParentComponent-------
@Component(modules = ParentModule.class)
public interface ParentComponent{
  //提供给子组件使用，必须显示声明
  Apple getApple();
}

//--------ChildComponent-------
@Component(dependencies = ParentComponeng.class, modules = ChildModule.class)
public interface ChildComponent{
  void inject(MainActivity activity);
}

//--------MainActivity-------
public class MainActivity extends AppCompatActivity{
  //可注入的Boy对象
  @Inject
  Boy boy;
  
  void onCreate(...){
    //...
    //获取父组件
    ParentComponent parentComponent = DaggerParentComponent.cretae();
    //获取子组件
    ChildComponent childComppnent = DaggerChildComponent
      .builder()
      .parentComponent(parentComponent)
      .build();
    //注入当前Activity
    childComppnent.inject(this);
    //此时boy已经被实例化，可调用其中的方法
    boy.eat();
  }
}
```



### 案例

- 使用Dagger主图User对象
- User
```
public class User {}
```

- UserModule

```java
@Module
public class UserModule {
    @Provides
    User provideUser() {
        return new User();
    }
}
```

- UserComponment

```java
@Component(modules = UserModule.class)
public interface UserComponent {
    void inject(MainActivity activity);
}
```

- MainActivity

```java
public class MainActivity extends AppCompatActivity {

    @Inject
    User user;
    @Inject
    User user1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        UserComponent component = DaggerUserComponent.create();
        component.inject(this);
        System.out.println("user.hashCode:" + user.hashCode());
        System.out.println("user1.hashCode:" + user1.hashCode());
    }
}
```

- 输出

```java
System.out: user.hashCode:14911969
System.out: user1.hashCode:166359814
```

- ##### 注意：上面的user和user1的hashCode值是不一样的，说明他们是不同的对象。如果想为User对象生成单例的话，就在UserModule和UserComponent中加入@Singleton注解，如下：

  ```java
  /**UserModule**/
  @Module
  public class UserModule {
      @Singleton
      @Provides
      User provideUser() {
          return new User();
      }
  }
  /**UserComponent**/
  @Singleton
  @Component(modules = UserModule.class)
  public interface UserComponent {
      void inject(MainActivity activity);
  }
  //MainActivity中的代码不变，此时的输出如下：
  //System.out: user.hashCode:14911969
  //System.out: user1.hashCode:14911969

  ```

  ​