package com.example.tangyi.jokes.Fragment;

import android.os.Bundle;
import android.app.Fragment;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.example.tangyi.jokes.Adapter.APIStoreAdapter;
import com.example.tangyi.jokes.Adapter.ListAdapter;
import com.example.tangyi.jokes.Bean.APIStoreBean;
import com.example.tangyi.jokes.R;
import com.example.tangyi.jokes.Bean.JsonBean;
import com.example.tangyi.jokes.Tools.CacheUtils;
import com.example.tangyi.jokes.Tools.MyListView;
import com.example.tangyi.jokes.Tools.TimeStamp;
import com.example.tangyi.jokes.Tools.URLList;
import com.example.tangyi.jokes.Tools.ViewHolder;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.internal.bind.DateTypeAdapter;
import com.google.gson.stream.JsonReader;
import com.lidroid.xutils.HttpUtils;
import com.lidroid.xutils.exception.HttpException;
import com.lidroid.xutils.http.RequestParams;
import com.lidroid.xutils.http.ResponseInfo;
import com.lidroid.xutils.http.callback.RequestCallBack;
import com.lidroid.xutils.http.client.HttpRequest;
import com.viewpagerindicator.TabPageIndicator;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by 在阳光下唱歌 on 2016/7/3.
 */
public class ContentFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener,
        MyListView.OnRefreshListener,ViewPager.OnPageChangeListener {
    private static final String[] TITLE=new String[]{"段子","笑话","随机"};
    private TabPageIndicator mIndicator;
    private ViewPager mViewPager;
    private View view1,view2,view3;
    private List<View> list;
    private SwipeRefreshLayout swipeLayout1,swipeLayout2,swipeLayout3;
    private MyListView textList1,textList2,textList3;
    private int page=2,page2=2,page3=2;
    private String s,s1;
    //Json数据对象
    private ArrayList<JsonBean.Result.Data> jokesDataList;
    private JsonBean.Result.Data data;
    private ArrayList<APIStoreBean.ShowApiResBody.Content> APIDataList;
    private APIStoreBean.ShowApiResBody.Content APIData;
    //ListView适配器
    private ListAdapter listAdapter;
    private APIStoreAdapter APIAdapter;
    private TimeStamp timeStamp=new TimeStamp();
    //缓存
    private String cache1,cache2;

    @Override
    public View onCreateView(LayoutInflater inflater,ViewGroup container,Bundle savedInstanceState) {
        View view=inflater.inflate(R.layout.content_fragment,container,false);
        mIndicator=(TabPageIndicator)view.findViewById(R.id.indicator);
        mViewPager=(ViewPager)view.findViewById(R.id.viewpager_home);
        //初始化ViewPager数据
        initViewPager();
        return view;
    }
    private void initViewPager(){
        view1=LayoutInflater.from(getActivity()).inflate(R.layout.viewpager_home1,null);
        view2=LayoutInflater.from(getActivity()).inflate(R.layout.viewpager_home1,null);
        view3=LayoutInflater.from(getActivity()).inflate(R.layout.viewpager_home1,null);
        list = new ArrayList<>();
        list.add(view1);
        list.add(view2);
        list.add(view3);
        mViewPager.setAdapter(new PagerAdapter() {
            @Override
            public int getCount() {
                return list.size();
            }

            @Override
            public CharSequence getPageTitle(int position) {
                return TITLE[position%TITLE.length];
            }

            @Override
            public void destroyItem(ViewGroup container, int position, Object object) {
                container.removeView(list.get(position));
            }

            @Override
            public Object instantiateItem(ViewGroup container, int position) {
                container.addView(list.get(position));
                return list.get(position);
            }

            @Override
            public boolean isViewFromObject(View view, Object object) {
                return view==object;
            }
        });
        mIndicator.setViewPager(mViewPager);
        mViewPager.addOnPageChangeListener(this);

        //实例化文字页SwipeRefreshLayout.(Google官方下拉刷新控件)
        swipeLayout1 =(SwipeRefreshLayout) view1.findViewById(R.id.swiperefresh_layout);
        //下拉监听器，刷新回调函数（一般用于更新UI）
        swipeLayout1.setOnRefreshListener(this);
        //设置刷新圆圈的颜色。
        swipeLayout1.setColorSchemeResources(R.color.jokes_swipe_red);
        // 设置手指在屏幕下拉多少距离会触发下拉刷新，200差不多了。其实默认的距离也很合适。
        swipeLayout1.setDistanceToTriggerSync(200);
        //ListView在View1的布局文件中，就必须在该布局文件中寻找Id并实例化，否则会空指针异常。
        textList1 =(MyListView) view1.findViewById(R.id.home_list);
        //上拉加载监听
        textList1.setOnRefreshListener(this);

        swipeLayout2=(SwipeRefreshLayout) view2.findViewById(R.id.swiperefresh_layout);
        swipeLayout2.setOnRefreshListener(this);
        swipeLayout2.setColorSchemeResources(R.color.jokes_swipe_red);
        swipeLayout2.setDistanceToTriggerSync(200);
        textList2 =(MyListView) view2.findViewById(R.id.home_list);
        textList2.setOnRefreshListener(this);

        swipeLayout3=(SwipeRefreshLayout) view3.findViewById(R.id.swiperefresh_layout);
        swipeLayout3.setOnRefreshListener(this);
        swipeLayout3.setColorSchemeResources(R.color.jokes_swipe_red);
        swipeLayout3.setDistanceToTriggerSync(200);
        textList3 =(MyListView) view3.findViewById(R.id.home_list);
        textList3.setOnRefreshListener(this);

        //写入缓存
        cache1=CacheUtils.getCache(getActivity(),URLList.CATEGORY_URL1 + 1 + URLList.CATEGORY_URL2);
        if (!TextUtils.isEmpty(cache1)){
            processData(cache1,false,0);
        }
        //初始化ViewPager数据
        getDataFromServer(0);
    }

    //利用Xutils框架请求数据
    private void getDataFromServer(int position){
        HttpUtils utils = new HttpUtils();
        switch (position) {
            case 0:
                //send就是发送请求。参数一代表获取数据。参数二是请求API的地址，
                utils.send(HttpRequest.HttpMethod.GET, URLList.CATEGORY_URL1 + 1 + URLList.CATEGORY_URL2,
                        //请求的是什么内容，泛型就写入相对应的数据类型。
                        new RequestCallBack<String>() {
                            //请求成功
                            @Override
                            public void onSuccess(ResponseInfo<String> responseInfo) {
                                String result = responseInfo.result;
                                processData(result, false,0);
                                //写入缓存
                                CacheUtils.setCache(getActivity(),
                                        URLList.CATEGORY_URL1 + 1 + URLList.CATEGORY_URL2,result);
                            }

                            //请求失败
                            @Override
                            public void onFailure(HttpException e, String s) {
                                e.printStackTrace();
                            }

                        });
                break;
            case 1:
                RequestParams requestParams=new RequestParams();
                requestParams.addHeader("apikey", URLList.APISTORE_KEY);
                utils.send(HttpRequest.HttpMethod.GET, URLList.APISTORE_TEXT_URL+1,requestParams
                        ,new RequestCallBack<String>() {
                            @Override
                            public void onFailure(HttpException e, String s) {
                                e.printStackTrace();
                            }

                            @Override
                            public void onSuccess(ResponseInfo<String> responseInfo) {
                                s1=s;
                                String result = responseInfo.result;
                                processData(result, false,1);
                                //写入缓存
                                CacheUtils.setCache(getActivity(),
                                        URLList.APISTORE_TEXT_URL+1,result);
                            }
                        });
                break;
            case 2:
                s = timeStamp.getTimeStamp(8);
                utils.send(HttpRequest.HttpMethod.GET, URLList.TWO_CATEGORY_URL1 + 1 + URLList.TWO_CATEGORY_URL2+s,
                        //请求的是什么内容，泛型就写入相对应的数据类型。
                        new RequestCallBack<String>() {
                            //请求成功
                            @Override
                            public void onSuccess(ResponseInfo<String> responseInfo) {
                                s1=s;
                                String result = responseInfo.result;
                                processData(result, false,2);

                            }

                            //请求失败
                            @Override
                            public void onFailure(HttpException e, String s) {
                                e.printStackTrace();
                            }

                        });
                break;

            default:
                break;
        }
    }

    /**
     * processData()函数用于解析Json数据
     * 使用Gson框架解析数据
     * 该方法中第一个参数为服务器传回的数据源，第二个参数作为标记，用于将ListView的数据进行合并，表示更多数据
     * 当isMore为非状态时，也就是不是更多数据的状态时，数据正常显示
     * 否则的话，也就是加载了更多的数据时，将更多数据的Json对象重新创建，并追加到原来的ListView中，进行合并
     */

    private void processData(String json,boolean isMore,int position){

        /**
         * gson.fromJson()函数意思是将Json数据转换为JAVA对象。
         * Json数据中字段对应的内容就是JAVA对象中字段内所存储的内容。
         * JsonReader reader = new JsonReader(new StringReader(json));
         * reader.setLenient(true);
         * 这两句代码的意思是设置JSON数据解析时为宽松模式，不容易OOM
         */
        Gson gson = new Gson();
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setLenient(true);
        switch (position) {
            case 0:
                try {
                    JsonBean fromJson = gson.fromJson(reader, JsonBean.class);
                    if (!isMore) {
                        //定义Json数据对象。
                        jokesDataList = fromJson.getResult().getData();
                        listAdapter = new ListAdapter(getActivity(), jokesDataList, data);
                        //当数据对象不为null时，也就是里面有数据时，设置适配器用于填充进ListView.
                        if (jokesDataList != null) {
                            textList1.setAdapter(listAdapter);
                        }
                    } else {
                        //加载了更多数据的时候，重新创建Json数据对象
                        ArrayList<JsonBean.Result.Data> moreJokesData = fromJson.getResult().getData();
                        //追加到第一个数据集合中，进行合并
                        jokesDataList.addAll(moreJokesData);
                        //刷新ListView
                        listAdapter.notifyDataSetChanged();
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
                break;
            case 1:
                try {
                    APIStoreBean APIJson = gson.fromJson(reader, APIStoreBean.class);
                    if (!isMore) {
                        APIDataList = APIJson.getShowapi_res_body().getContentlist();
                        APIAdapter = new APIStoreAdapter(getActivity(), APIDataList, APIData);
                        if (APIDataList != null) {
                            textList2.setAdapter(APIAdapter);
                        }
                    } else {
                        ArrayList<APIStoreBean.ShowApiResBody.Content> moreAPIData = APIJson.getShowapi_res_body().getContentlist();
                        APIDataList.addAll(moreAPIData);
                        APIAdapter.notifyDataSetChanged();
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
                break;
            case 2:
                try {


                    JsonBean fromJson2 = gson.fromJson(reader, JsonBean.class);
                    if (!isMore) {
                        //定义Json数据对象。
                        jokesDataList = fromJson2.getResult().getData();
                        listAdapter = new ListAdapter(getActivity(), jokesDataList, data);
                        //当数据对象不为null时，也就是里面有数据时，设置适配器用于填充进ListView.
                        if (jokesDataList != null) {
                            textList3.setAdapter(listAdapter);
                        }
                    } else {
                        ArrayList<JsonBean.Result.Data> moreJokesData = fromJson2.getResult().getData();
                        jokesDataList.addAll(moreJokesData);
                        listAdapter.notifyDataSetChanged();
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
                break;
            default:
                break;
        }

    }




    //下拉刷新回调
    @Override
    public void onRefresh() {
        switch (mViewPager.getCurrentItem()) {
            case 0:
                getDataFromServer(0);
                //设置刷新完成，进度圆圈圈停止转动。
                swipeLayout1.setRefreshing(false);
                break;
            case 1:
                getDataFromServer(1);
                swipeLayout2.setRefreshing(false);
                break;
            case 2:
                getDataFromServer(2);
                swipeLayout3.setRefreshing(false);
                break;
            default:
                break;
        }
    }
    /**
     * 当加载更多数据时，就调用此方法
     * 请求成功后，在onSuccess()方法中调用processData()方法进行数据解析时，第二个参数传入的是true
     * 就会执行该方法中if语句中的else语句。
     */
    private void getMoreDataFromServer(){
        HttpUtils utils=new HttpUtils();
        switch (mViewPager.getCurrentItem()) {
            case 0:
                //send就是发送请求。参数一代表获取数据。参数二是请求API的地址，
                utils.send(HttpRequest.HttpMethod.GET, URLList.CATEGORY_URL1 + page + URLList.CATEGORY_URL2,
                        new RequestCallBack<String>() {

                            //请求成功
                            @Override
                            public void onSuccess(ResponseInfo<String> responseInfo) {
                                String result = responseInfo.result;
                                processData(result, true,0);
                            }

                            //请求失败
                            @Override
                            public void onFailure(HttpException e, String s) {
                                e.printStackTrace();
                            }


                        });
                break;
            case 1:
                RequestParams requestParams=new RequestParams();
                requestParams.addHeader("apikey", URLList.APISTORE_KEY);
                    //send就是发送请求。参数一代表获取数据。参数二是请求API的地址，
                    utils.send(HttpRequest.HttpMethod.GET,URLList.APISTORE_TEXT_URL+page2,requestParams
                            , new RequestCallBack<String>() {

                                //请求成功
                                @Override
                                public void onSuccess(ResponseInfo<String> responseInfo) {
                                    String result=responseInfo.result;
                                    processData(result,true,1);
                                }

                                //请求失败
                                @Override
                                public void onFailure(HttpException e, String s) {
                                    e.printStackTrace();
                                }



                            });

                break;
            case 2:
                utils.send(HttpRequest.HttpMethod.GET, URLList.TWO_CATEGORY_URL1 + page3 + URLList.TWO_CATEGORY_URL2+s1,
                        //请求的是什么内容，泛型就写入相对应的数据类型。
                        new RequestCallBack<String>() {
                            //请求成功
                            @Override
                            public void onSuccess(ResponseInfo<String> responseInfo) {
                                String result = responseInfo.result;
                                processData(result,true,2);

                            }

                            //请求失败
                            @Override
                            public void onFailure(HttpException e, String s) {
                                e.printStackTrace();
                            }

                        });
                break;
            default:
                break;

        }
    }
    //上拉加载回调
    @Override
    public void onLoadMore() {
        switch (mViewPager.getCurrentItem()) {
            case 0:
                getMoreDataFromServer();
                page++;
                break;
            case 1:
                getMoreDataFromServer();
                page2++;
                break;
            case 2:
                getMoreDataFromServer();
                page3++;
                break;
            default:
                break;
        }
    }
    //ViewPager滑动回调，onPageScrolled()是滑动结束回调
    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {
        getDataFromServer(position);
        switch (position){
            case 0:
                cache1=CacheUtils.getCache(getActivity(),URLList.CATEGORY_URL1 + 1 + URLList.CATEGORY_URL2);
                if (!TextUtils.isEmpty(cache1)){
                    processData(cache1,false,position);
                }
                break;
            case 1:
                cache2=CacheUtils.getCache(getActivity(),URLList.APISTORE_TEXT_URL+1);
                if (!TextUtils.isEmpty(cache2)){
                    processData(cache2,false,position);
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }
}
