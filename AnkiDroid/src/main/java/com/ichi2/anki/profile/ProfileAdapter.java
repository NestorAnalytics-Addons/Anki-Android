package com.ichi2.anki.profile;

import android.content.Context;
import android.preference.EditTextPreference;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.PreferenceContext;
import com.ichi2.anki.Preferences;
import com.ichi2.anki.R;
import com.ichi2.anki.exception.StorageAccessException;

import java.util.prefs.PreferenceChangeListener;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import timber.log.Timber;

public class ProfileAdapter extends RecyclerView.Adapter<ProfileAdapter.ViewHolder> {
    Context context;
    LayoutInflater inflater;
    String[] pathList;


    public ProfileAdapter(Context context, String[] pathList) {
        this.context = context;
        inflater = LayoutInflater.from(context);
        this.pathList = pathList;
    }


    @NonNull
    @Override
    public ProfileAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = inflater.inflate(R.layout.item_profile_path, parent, false);
        ViewHolder holder = new ViewHolder(view);

        return holder;
    }


    @Override
    public void onBindViewHolder(@NonNull ProfileAdapter.ViewHolder holder, int position) {
        holder.path.setText(pathList[position]);
    }

    @Override
    public int getItemCount() {
        return pathList.length;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        TextView path;
        String str;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            path = itemView.findViewById(R.id.text_path);

            path.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {


                }
            });
        }
    }
}
