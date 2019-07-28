package io.dashbase.clue.client;


import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.util.Collection;

public interface ClueCommandService {
    @GET("/clue/command/{cmd}")
    Call<ResponseBody> command(@Path("cmd") String cmd, @Query("args") String args);

    @GET("/clue/commands")
    Call<Collection<String>> commands();
}
