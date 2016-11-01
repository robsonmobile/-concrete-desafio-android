package com.github.brunodles.githubpopular.app.view.pull_request_list;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;

import com.github.brunodles.githubpopular.api.GithubEndpoint;
import com.github.brunodles.githubpopular.api.dto.Repository;
import com.github.brunodles.githubpopular.app.R;
import com.github.brunodles.githubpopular.app.application.GithubApplication;
import com.github.brunodles.githubpopular.app.databinding.ActivityListPullRequestBinding;
import com.github.brunodles.githubpopular.app.databinding.NavigationDrawerLayoutBinding;
import com.github.brunodles.githubpopular.app.view.repository_list.RepositoryAdapter;
import com.github.brunodles.githubpopular.app.view.ToolbarTipOffsetListener;
import com.github.brunodles.recyclerview.EndlessRecyclerOnScrollListener;
import com.github.brunodles.recyclerview.VerticalSpaceItemDecoration;
import com.github.brunodles.utils.LogRx;
import com.trello.rxlifecycle.android.ActivityEvent;
import com.trello.rxlifecycle.components.support.RxAppCompatActivity;

import org.parceler.Parcels;

import java.util.ArrayList;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.CompositeSubscription;

import static com.github.brunodles.utils.DimensionUtils.fromDp;

/**
 * Created by bruno on 31/10/16.
 */

public class PullRequestsActivity extends RxAppCompatActivity {

    private static final String TAG = "PullRequestsActivity";
    public static final String STATE_LIST = "state_list";
    public static final String EXTRA_REPOSITORY = "EXTRA_REPOSITORY";

    private NavigationDrawerLayoutBinding navigationDrawer;
    private ActivityListPullRequestBinding binding;
    private RepositoryAdapter repositoryAdapter;
    private CompositeSubscription subscriptions;
    private GithubEndpoint github;

    public static Intent newIntent(Context context, Repository repository) {
        Intent intent = new Intent(context, PullRequestsActivity.class);
        intent.putExtra(EXTRA_REPOSITORY, Parcels.wrap(repository));
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LayoutInflater layoutInflater = getLayoutInflater();
        navigationDrawer = NavigationDrawerLayoutBinding.inflate(layoutInflater);
        binding = ActivityListPullRequestBinding.inflate(layoutInflater, navigationDrawer.navigationContainer, true);
        setContentView(navigationDrawer.getRoot());

        setupToolbar(binding.toolbar);
        binding.appbar.addOnOffsetChangedListener(new ToolbarTipOffsetListener(binding.toolbar, binding.toolbarTip));

        subscriptions = new CompositeSubscription();
        github = GithubApplication.githubApi();

        Intent intent = getIntent();
        Repository repository = Parcels.unwrap(intent.getParcelableExtra(EXTRA_REPOSITORY));
        binding.setRepository(repository);
//        setupRecyclerView(binding.recyclerView);

        lifecycle().filter(event -> event == ActivityEvent.DESTROY)
                .subscribe(e -> subscriptions.unsubscribe());
    }

    private void setupToolbar(Toolbar toolbar) {
        toolbar.setNavigationIcon(R.drawable.ic_menu);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView(RecyclerView recyclerView) {
        repositoryAdapter = new RepositoryAdapter();
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this,
                LinearLayoutManager.VERTICAL, false);

        repositoryAdapter.setUserProvider(github::user);
        repositoryAdapter.setOnItemClickListener(this::onItemClick);

        recyclerView.setAdapter(repositoryAdapter);
        recyclerView.setLayoutManager(linearLayoutManager);
        VerticalSpaceItemDecoration itemDecoration = new VerticalSpaceItemDecoration(
                (int) fromDp(getResources(), 4));
        recyclerView.addItemDecoration(itemDecoration);

        EndlessRecyclerOnScrollListener scrollListener =
                new EndlessRecyclerOnScrollListener(linearLayoutManager, this::loadPage);
        recyclerView.addOnScrollListener(scrollListener);

        lifecycle().filter(event -> event == ActivityEvent.DESTROY)
                .subscribe(e -> recyclerView.removeOnScrollListener(scrollListener));
    }

    private void onItemClick(Integer integer, Repository repository) {
    }

    private void loadPage(int page) {
        Subscription subscription = github.searchRepositories("language:Java", "star", page)
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .map(e -> e.items)
                .subscribe(repositoryAdapter::addList,
                        LogRx.e(TAG, "loadPage: failed to load page " + page));
        subscriptions.add(subscription);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        ArrayList<Repository> list = Parcels.unwrap(savedInstanceState.getParcelable(STATE_LIST));
        repositoryAdapter.setList(list);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Parcelable wrap = Parcels.wrap(new ArrayList<>(repositoryAdapter.getList()));
        outState.putParcelable(STATE_LIST, wrap);
    }
}