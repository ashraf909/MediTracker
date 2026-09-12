package com.meditracker.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.meditracker.app.R;
import com.meditracker.app.model.MedicineDose;
import java.util.ArrayList;
import java.util.List;

public class MedicineAdapter extends RecyclerView.Adapter<MedicineAdapter.Holder> {
    public interface Actions { void onPrimary(MedicineDose dose); void onEdit(MedicineDose dose); void onDelete(MedicineDose dose); }
    private final boolean patientMode;
    private final Actions actions;
    private final List<MedicineDose> values = new ArrayList<>();

    public MedicineAdapter(boolean patientMode, Actions actions) { this.patientMode = patientMode; this.actions = actions; }
    public void submit(List<MedicineDose> next) { values.clear(); values.addAll(next); notifyDataSetChanged(); }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_medicine_dose, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        MedicineDose dose = values.get(position);
        android.content.Context context = holder.itemView.getContext();
        holder.time.setText(dose.getTiming()); holder.name.setText(dose.displayName(context));
        holder.detail.setText(context.getString(R.string.medicine_summary, dose.displayDosage(context), dose.mealLabel(context)));
        holder.status.setText(dose.isTaken() ? R.string.status_taken : R.string.status_pending);
        holder.primary.setText(patientMode ? R.string.i_took_medicine : R.string.send_reminder);
        holder.primary.setEnabled(!dose.isTaken());
        holder.edit.setVisibility(patientMode ? View.GONE : View.VISIBLE); holder.delete.setVisibility(patientMode ? View.GONE : View.VISIBLE);
        holder.primary.setOnClickListener(v -> actions.onPrimary(dose));
        holder.edit.setOnClickListener(v -> actions.onEdit(dose)); holder.delete.setOnClickListener(v -> actions.onDelete(dose));
    }
    @Override public int getItemCount() { return values.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        TextView time, name, detail, status; Button primary, edit, delete;
        Holder(View item) { super(item); time = item.findViewById(R.id.timeText); name = item.findViewById(R.id.nameText); detail = item.findViewById(R.id.detailText); status = item.findViewById(R.id.statusText); primary = item.findViewById(R.id.primaryButton); edit = item.findViewById(R.id.editButton); delete = item.findViewById(R.id.deleteButton); }
    }
}
