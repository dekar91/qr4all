package ru.dekar.qr4all.ui;

import android.app.Activity;
import android.app.ActivityOptions;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.transition.Explode;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.List;

import ru.dekar.qr4all.AppExecutors;
import ru.dekar.qr4all.R;
import ru.dekar.qr4all.database.AppDatabase;
import ru.dekar.qr4all.database.ItemViewModel;
import ru.dekar.qr4all.mocks.ItemMocks;
import ru.dekar.qr4all.models.ItemEntity;
import ru.dekar.qr4all.parcode.BarcodeCaptureActivity;
import ru.dekar.qr4all.services.UpdateItemService;

/**
 * An activity representing a list of Items. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link ItemDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
public class ItemListActivity extends AppCompatActivity{

    private FirebaseAnalytics mFirebaseAnalytics;

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;

    private static final int RC_BARCODE_CAPTURE = 9001;

    public AppDatabase mDb;

    public List<ItemEntity> mItemEntities;

    public SimpleItemRecyclerViewAdapter mItemAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setExitTransition(new Explode());
        getWindow().setEnterTransition(new Explode());
        setContentView(R.layout.activity_item_list);
        mDb = AppDatabase.getsInstance(this);
        if (findViewById(R.id.item_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;

            mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        }

        UpdateItemService.startUpdateItemService(this, null);

        View recyclerView = findViewById(R.id.item_list);
        assert recyclerView != null;
        setupRecyclerView((RecyclerView) recyclerView);

        FloatingActionButton button = findViewById(R.id.buttonScanQr);

        final Activity act = this;
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(act, BarcodeCaptureActivity.class);
                intent.putExtra(BarcodeCaptureActivity.AutoFocus, true);
                intent.putExtra(BarcodeCaptureActivity.UseFlash, true);

                startActivityForResult(intent, RC_BARCODE_CAPTURE);
            }
        });
    }


    public void setViewModel() {
        ItemViewModel viewModel = ViewModelProviders.of(this).get(ItemViewModel.class);
        viewModel.getItems().observe(this, new Observer<List<ItemEntity>>() {
            @Override
            public void onChanged(@Nullable List<ItemEntity> itemEntities) {

                if (itemEntities.size() < 10)
                {
                    AppExecutors.getInstance().diskIO().execute(new Runnable() {
                        @Override
                        public void run() {
                            List<ItemEntity> mocks = ItemMocks.getMocks();
                            for (int i = 1; i < mocks.size(); i++) {
                                mDb.itemDao().insertItem(mocks.get(i));
                            };
                        }
                    });
                }


                mItemEntities = itemEntities;
                mItemAdapter.setmValues(mItemEntities);
            }
        });

    }


    private void setupRecyclerView(@NonNull RecyclerView recyclerView) {
        DividerItemDecoration decorator = new DividerItemDecoration(getApplicationContext(), DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(decorator);
        mItemAdapter = new SimpleItemRecyclerViewAdapter(this, mTwoPane);
        recyclerView.setAdapter(mItemAdapter);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(final RecyclerView.ViewHolder viewHolder, int direction) {
                AppExecutors.getInstance().diskIO().execute(new Runnable() {
                    @Override
                    public void run() {
                        int position = viewHolder.getAdapterPosition();
                        mDb.itemDao().deleteItem(mItemEntities.get(position));
                    }
                });

            }
        }).attachToRecyclerView(recyclerView);
    }

    public static class SimpleItemRecyclerViewAdapter
            extends RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder> {

        private final ItemListActivity mParentActivity;
        private  List<ItemEntity> mValues;
        private final boolean mTwoPane;
        private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ItemEntity item = (ItemEntity) view.getTag();
                if (mTwoPane) {
                    Bundle arguments = new Bundle();
                    arguments.putString(ItemDetailFragment.ARG_ITEM_ID, String.valueOf(item.getId()));
                    ItemDetailFragment fragment = new ItemDetailFragment();
                    fragment.setArguments(arguments);
                    mParentActivity.getFragmentManager().beginTransaction()
                            .replace(R.id.item_detail_container, fragment)
                            .commit();
                } else {
                    Context context = view.getContext();
                    Intent intent = new Intent(context, ItemDetailActivity.class);
                    intent.putExtra(ItemDetailFragment.ARG_ITEM_ID, String.valueOf(item.getId()));
                    Bundle transitionBundle = ActivityOptions.makeSceneTransitionAnimation(mParentActivity).toBundle();

                    context.startActivity(intent, transitionBundle);
                }
            }
        };

        public void setmValues(List<ItemEntity> itemEntities)
        {
            mValues = itemEntities;
            this.notifyDataSetChanged();

        }

        SimpleItemRecyclerViewAdapter(ItemListActivity parent,
                                      boolean twoPane) {
            mParentActivity = parent;
            mTwoPane = twoPane;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_list_content, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            holder.mContentView.setText(mValues.get(position).getName());

            holder.itemView.setTag(mValues.get(position));
            holder.itemView.setOnClickListener(mOnClickListener);
        }

        @Override
        public int getItemCount() {
            if(mValues == null)
                return 0;

            return mValues.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView mContentView;

            ViewHolder(View view) {
                super(view);
                mContentView = (TextView) view.findViewById(R.id.content);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setViewModel();

        UpdateItemService.startUpdateItemService(this, null);

    }
}
