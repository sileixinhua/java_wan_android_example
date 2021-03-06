package json.chao.com.wanandroid.di.module;

import com.facebook.stetho.okhttp3.StethoInterceptor;
import com.franmontiel.persistentcookiejar.PersistentCookieJar;
import com.franmontiel.persistentcookiejar.cache.SetCookieCache;
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor;

import java.io.File;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import json.chao.com.wanandroid.app.WanAndroidApp;
import json.chao.com.wanandroid.performance.net.OkHttpDns;
import json.chao.com.wanandroid.performance.net.OkHttpEvent;
import json.chao.com.wanandroid.performance.net.OkHttpEventListener;
import me.jessyan.retrofiturlmanager.RetrofitUrlManager;
import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import json.chao.com.wanandroid.core.http.api.GeeksApis;
import json.chao.com.wanandroid.BuildConfig;
import json.chao.com.wanandroid.app.Constants;
import json.chao.com.wanandroid.di.qualifier.WanAndroidUrl;
import json.chao.com.wanandroid.utils.CommonUtils;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * @author quchao
 * @date 2017/11/27
 */

@Module
public class HttpModule {

    @Singleton
    @Provides
    GeeksApis provideGeeksApi(@WanAndroidUrl Retrofit retrofit) {
        return retrofit.create(GeeksApis.class);
    }

    @Singleton
    @Provides
    @WanAndroidUrl
    Retrofit provideGeeksRetrofit(Retrofit.Builder builder, OkHttpClient client) {
        return createRetrofit(builder, client, GeeksApis.HOST);
    }

    @Singleton
    @Provides
    Retrofit.Builder provideRetrofitBuilder() {
        return new Retrofit.Builder();
    }

    @Singleton
    @Provides
    OkHttpClient.Builder provideOkHttpBuilder() {
        return new OkHttpClient.Builder();
    }

    @Singleton
    @Provides
    OkHttpClient provideClient(OkHttpClient.Builder builder) {
        // ????????????????????????
        builder.eventListenerFactory(OkHttpEventListener.FACTORY);
        // httpDns ??????
        builder.dns(OkHttpDns.getIns(WanAndroidApp.getAppComponent().getContext()));

        File cacheFile = new File(Constants.PATH_CACHE);
        Cache cache = new Cache(cacheFile, 1024 * 1024 * 50);
        Interceptor cacheInterceptor = chain -> {
            Request request = chain.request();
            if (!CommonUtils.isNetworkConnected()) {
                // ????????????????????????????????????????????????????????????
                request = request.newBuilder()
                        .cacheControl(CacheControl.FORCE_CACHE)
                        .build();
            }
            Response response = chain.proceed(request);
            if (CommonUtils.isNetworkConnected()) {
                int maxAge = 0;
                // ????????????, ?????????, ?????????????????????0
                response.newBuilder()
                        .header("Cache-Control", "public, max-age=" + maxAge)
                        .removeHeader("Pragma")
                        .build();
            } else {
                // ??????????????????????????????4???
                int maxStale = 60 * 60 * 24 * 28;
                response.newBuilder()
                        .header("Cache-Control", "public, only-if-cached, max-stale=" + maxStale)
                        .removeHeader("Pragma")
                        .build();
            }
            return response;
        };
        // ????????????
        builder.addNetworkInterceptor(cacheInterceptor);
        builder.addInterceptor(cacheInterceptor);
        builder.cache(cache);

        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
            builder.addInterceptor(loggingInterceptor);
            builder.addNetworkInterceptor(new StethoInterceptor());
        }

        //????????????
        builder.connectTimeout(10, TimeUnit.SECONDS);
        builder.readTimeout(20, TimeUnit.SECONDS);
        builder.writeTimeout(20, TimeUnit.SECONDS);
        //????????????
        builder.retryOnConnectionFailure(true);
        //cookie??????
        builder.cookieJar(new PersistentCookieJar(new SetCookieCache(),
                new SharedPrefsCookiePersistor(WanAndroidApp.getInstance())));
        return RetrofitUrlManager.getInstance().with(builder).build();
    }

    private Retrofit createRetrofit(Retrofit.Builder builder, OkHttpClient client, String url) {
        return builder
                .baseUrl(url)
                .client(client)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
}
