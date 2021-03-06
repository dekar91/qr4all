package ru.dekar.qr4all.ui;

import android.app.ActionBar;
import android.app.ActivityOptions;
import android.app.Fragment;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.squareup.picasso.Picasso;

import butterknife.BindView;
import butterknife.ButterKnife;
import ru.dekar.qr4all.AppExecutors;
import ru.dekar.qr4all.R;
import ru.dekar.qr4all.database.AppDatabase;
import ru.dekar.qr4all.database.UpdateItemViewModel;
import ru.dekar.qr4all.database.UpdateItemViewModelFactory;
import ru.dekar.qr4all.models.ItemEntity;
import ru.dekar.qr4all.services.UpdateItemService;

/**
 * A fragment representing a single Item detail screen.
 * This fragment is either contained in a {@link ItemListActivity}
 * in two-pane mode (on tablets) or a {@link ItemDetailActivity}
 * on handsets.
 */
public class ItemDetailFragment extends Fragment {
    @BindView(R.id.inputItemName)      EditText inputItemName;
    @BindView(R.id.inputItemDetails)   EditText inputItemDetails;
    @BindView(R.id.itemId)             TextView itemIdView;
    @BindView(R.id.itemPhoto)          ImageView itemPhoto;

    private FirebaseAnalytics mFirebaseAnalytics;

    /**
     * The fragment argument representing the item ID that this fragment
     * represents.
     */
    public static final String ARG_ITEM_ID = "item_id";

    /**
     * The dummy content this fragment is presenting.
     */
    private int itemId;

    public ItemEntity mItemEntity;
    public View rootView;


    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ItemDetailFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments().containsKey(ARG_ITEM_ID)) {
            String arg = getArguments().getString(ARG_ITEM_ID);

            if(arg == null)
            {
                Toast toast = Toast.makeText(getActivity(), R.string.barcode_failure, Toast.LENGTH_SHORT);
                toast.show();
                Intent intent = new Intent(getActivity(), ItemListActivity.class);
                Bundle transitionBundle = ActivityOptions.makeSceneTransitionAnimation(getActivity()).toBundle();

                getContext().startActivity(intent, transitionBundle);

                return;
            }
            itemId = Integer.parseInt(getArguments().getString(ARG_ITEM_ID));
        }

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(getActivity());


    }

    public void updateItem(View rootView) {
        String newName = inputItemName.getText().toString();
        String newDetails = inputItemDetails.getText().toString();
        if (mItemEntity != null && (mItemEntity.getName().equals(newName) || mItemEntity.getDetails().equals(newDetails))) {
            mItemEntity.setName(newName);
            mItemEntity.setDetails(newDetails);

            AppExecutors.getInstance().diskIO().execute(new Runnable() {
                @Override
                public void run() {
                    AppDatabase mDatabase = AppDatabase.getsInstance(getContext());

                    mDatabase.itemDao().updateItem(mItemEntity);
                }
            });
        }
    }

    public void updateUi(final View rootView, final int itemId) {
        final AppCompatActivity act = (AppCompatActivity) getActivity();

        AppDatabase mDb = AppDatabase.getsInstance(act);

        UpdateItemViewModelFactory factory = new UpdateItemViewModelFactory(mDb, itemId);

        final UpdateItemViewModel viewModel = ViewModelProviders.of(act, factory).get(UpdateItemViewModel.class);
        viewModel.getItem().observe(act, new Observer<ItemEntity>() {
            @Override
            public void onChanged(@Nullable ItemEntity itemEntity) {
                if (itemEntity != null) {
                    mItemEntity = itemEntity;
                    ItemDetailActivity.mItemEntity = mItemEntity;

                    inputItemName.setText(mItemEntity.getDetails());
                    inputItemDetails.setText(mItemEntity.getName());
                    itemIdView.setText(String.valueOf(mItemEntity.getId()));

                    itemPhoto.setContentDescription(R.string.itemPhoto_ContentDescription + " " + mItemEntity.getName());

                    Picasso.get().load(Uri.parse(mItemEntity.getImageUrl())).into(itemPhoto);
                    act.setTitle(mItemEntity.getName());

                    // Update widget
                    UpdateItemService.startUpdateItemService(getContext(), mItemEntity);
                } else {
                    Toast toast = Toast.makeText(getActivity(), R.string.barcode_failure, Toast.LENGTH_SHORT);
                    toast.show();
                    Intent intent = new Intent(getActivity(), ItemListActivity.class);
                    getContext().startActivity(intent);

                    // Update widget
                    UpdateItemService.startUpdateItemService(getContext(), null);
                }
            }

        });

    }

    @Override
    public void onResume() {
        super.onResume();
        updateItem(rootView);
    }

    @Override
    public void onPause() {
        super.onPause();
        updateItem(rootView);

    }

    @Override
    public void onStop() {
        super.onStop();

        updateItem(rootView);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        rootView = inflater.inflate(R.layout.item_detail, container, false);
        ButterKnife.bind(this, rootView);

        Toolbar profile_toolbar = (Toolbar) rootView.findViewById(R.id.my_toolwbar);
        AppCompatActivity activity = (AppCompatActivity)getActivity();
        activity.setSupportActionBar(profile_toolbar);
        android.support.v7.app.ActionBar actionBar = activity.getSupportActionBar();
        if(actionBar != null)
            actionBar.setDisplayShowHomeEnabled(true);
        try{
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);

        } catch (Exception e) {
            Log.v("itemfragment", "Can not setUp Up button");
        }

        updateUi(rootView, itemId);

        return rootView;
    }

}