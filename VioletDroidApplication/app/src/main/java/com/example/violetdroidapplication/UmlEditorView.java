package com.example.violetdroidapplication;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * A View to be used inside an Activity
 * This View will be used as the main editor for Class Diagrams
 */
public class UmlEditorView extends View {
    /** used for saving file */
    public static final String ITEMS_KEY = "items";
    private static final String FILE_TYPE = "class_diagram";
    private static final String TAG = "ClassDiagEditorView";

    //Items: everything here needs to be saved/loaded
    private ArrayList<UmlDrawable> allClassDrawables;

    private UmlDrawable selected = null; //null means none are selected
    private Context ctx;

    private int x;  // starting x-coordinate of touch
    private int y;  // starting y-coordinate of touch
    private boolean draggable;  // whether selected item is ready to be dragged

    // handles long presses
    private Handler handler;
    private Runnable longPress;
    private boolean isLongPressed = false;

    //only used temporarily
    private UmlNode newArrowHelper;
    private boolean waitingForArrowInput = false;

    private boolean savePending = false;

    /**
     * @param context of this application
     */
    public UmlEditorView(Context context) {
        this(context, null);
    }

    /**
     * Create a new editor view
     *
     * @param ctx   of this application
     * @param attrs attributes used to create this View
     */
    public UmlEditorView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        this.ctx = ctx;
        allClassDrawables = new ArrayList<>();

        handler = new Handler();
        longPress = new Runnable() {
            public void run() {
                isLongPressed = true;
                if (findItem(x, y) != null) {
                    selected = findItem(x, y);
                    postInvalidate();
                }
                Log.i(TAG, "Long press");
            }
        };
    }

    /**
     * implementation things to draw
     * @param canvas to draw items
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Log.d(TAG, "onDraw, selected: " + selected);

        for (UmlDrawable drawable : allClassDrawables)
            drawable.draw(canvas, selected == drawable);

    }

    /**
     * Used to handle touch events
     * @param event type of MotionEvent
     * @return true if event was handled here, false otherwise
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // longPress will be called in 800 ms if not cancelled
                if (waitingForArrowInput) {
                    UmlDrawable tapped = findItem((int) event.getX(), (int) event.getY());
                    if (tapped != null && tapped instanceof UmlNode) {
                        newArrowHelper = (UmlNode) tapped;
                        waitingForArrowInput = false;
                    }
                } else {
                    handler.postDelayed(longPress, 800);
                    Log.i(TAG, "onTouchEvent: ACTION_DOWN [" + x + "," + y + "]");
                    x = Math.round(event.getX());
                    y = Math.round(event.getY());
                    if (findItem(x, y) != null) {
                        if (findItem(x, y).equals(selected)) {
                            draggable = true;
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
                // call to longPress cancelled (if press&hold < 800 ms)
                handler.removeCallbacks(longPress);

                // code for handling short taps
                if (Math.abs(event.getX() - x) <= 2 && !isLongPressed) {
                    Log.i(TAG, "onTouchEvent: ACTION_UP - is a tap");
                    // deselect item
                    if (selected != null) {
                        if (selected.equals(findItem(x, y))) {
                            selected = null;
                        }
                    }
                    postInvalidate();
                    return false;
                }

                Log.i(TAG, "onTouchEvent: ACTION_UP - not a tap");
                isLongPressed = false;
                draggable = false;
                break;

            case MotionEvent.ACTION_MOVE:
                // call to longPress cancelled (if press&hold < 800 ms)
                handler.removeCallbacks(longPress);
                int moveX = Math.round(event.getX());
                int moveY = Math.round(event.getY());
                if (draggable && selected != null && selected instanceof UmlNode) {
                    UmlNode selectedShape = (UmlNode) selected;
                    selectedShape.set(moveX, moveY);  // move item by dragging
                    savePending = true;
                }

                break;
            default:
                break;
        }

        postInvalidate();
        return true;
    }

    /**
     * find an item at the given location
     *
     * @param x coordinate of location
     * @param y coordinate of location
     * @return item at the location, null if nothing is there
     */
    public UmlDrawable findItem(int x, int y) {
        Log.i(TAG, "findItem");

        for (UmlDrawable drawable : allClassDrawables)
            if (drawable.contains(x, y))
                return drawable;

        Log.d(TAG, "findItem: FOUND NOTHING: returning null");
        return null;
    }

    /**
     * If a UmlClassNode is already selected, this method will edit its attributes
     * Prompts the user to input attributes
     * Immediately updates the view by redrawing
     */
    public void addOrEditItem() {
        Log.d(TAG, "addOrEditItem");
        //we are editing an item if we already have one selected, later on selected can be an arrow
        final boolean editingItem = (selected != null && selected instanceof UmlClassNode);
        final LinearLayout inputHolders = new LinearLayout(ctx);
        inputHolders.setOrientation(LinearLayout.VERTICAL);
        //these EditTexts will lie inside the AlertDialog
        final EditText inputTitleView = new EditText(ctx);
        inputTitleView.setHint(R.string.class_diag_enter_title_hint);
        inputTitleView.setSingleLine();
        final EditText inputAttrsView = new EditText(ctx);
        inputAttrsView.setHint(R.string.class_diag_enter_attrs_hint);
        final EditText inputMethodsView = new EditText(ctx);
        inputMethodsView.setHint(R.string.class_diag_enter_methods_hint);
        if (editingItem) { //if we're editing an item, populate the dialog with the current contents
            inputTitleView.setText(((UmlClassNode) selected).getTitle());
            inputAttrsView.setText(((UmlClassNode) selected).getAttributes());
            inputMethodsView.setText(((UmlClassNode) selected).getMethods());
            inputTitleView.selectAll();
        }
        inputHolders.addView(inputTitleView);
        inputHolders.addView(inputAttrsView);
        inputHolders.addView(inputMethodsView);
        AlertDialog builder = new AlertDialog.Builder(ctx).create();
        builder.setTitle(editingItem ? R.string.class_diag_edit_title
                : R.string.class_diag_enter_title);
        builder.setView(inputHolders);
        builder.setButton(AlertDialog.BUTTON_POSITIVE, ctx.getString(R.string.done_str),
                (DialogInterface.OnClickListener) null); //OnClickListener overriden later
        builder.setButton(AlertDialog.BUTTON_NEGATIVE, ctx.getString(R.string.cancel_str),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }
        );
        builder.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            //using OnShowListener so we can keep the dialog shown if the same name alreay exists
            public void onShow(final DialogInterface dialog) {
                ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (editingItem) { //if we're editing an item, just update the contents
                            ((UmlClassNode) selected).setTexts(inputTitleView.getText().toString(),
                                    inputAttrsView.getText().toString(),
                                    inputMethodsView.getText().toString());
                            postInvalidate();
                            dialog.dismiss();
                        } else { // we are creating a new item
                            //check to see if the name already exists
                            boolean sameNameExists = false;
                            for (UmlDrawable shape : allClassDrawables) {
                                if (shape instanceof UmlClassNode && ((UmlClassNode) shape).getTitle()
                                        .equals(inputTitleView.getText().toString())) {
                                    sameNameExists = true;
                                    break;
                                }
                            }
                            if (sameNameExists) //warn the user that there already exists an item with that name
                                Toast.makeText(ctx, R.string.class_diag_err_name_exists, Toast.LENGTH_SHORT).show();
                            else {
                                // add the item
                                // 100, 100 in the following line is an arbitrary point
                                selected = new UmlClassNode(inputTitleView.getText().toString(),
                                        inputAttrsView.getText().toString(),
                                        //add new item AND select it
                                        inputMethodsView.getText().toString(), 100, 100);
                                allClassDrawables.add(selected);
                                dialog.dismiss();
                            }
                            savePending = true; //we've made changes to the editor
                            postInvalidate();
                        }
                    }
                });
            }
        });
        builder.show();
    }
    
    /**
     * method used to add or edit a note
     */
    public void addOrEditNote() {
        Log.d(TAG, "addOrEditNote");

        // we are editing a note if we already have one selected
        final boolean editingNote = (selected != null && selected instanceof UmlNoteNode);

        final LinearLayout inputHolders = new LinearLayout(ctx);
        inputHolders.setPadding(10, 0, 10, 0);
        inputHolders.setOrientation(LinearLayout.VERTICAL);
        final EditText inputTextView = new EditText(ctx);
        inputTextView.setHint(R.string.note_enter_text_hint);

        //if we're editing a note, populate the dialog with the current contents
        if (editingNote) {
            inputTextView.setText(((UmlNoteNode) selected).getText());
            inputTextView.selectAll();
        }

        inputHolders.addView(inputTextView);

        //create the AlertDialog
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(editingNote ? R.string.note_edit_title
                : R.string.note_enter_title);
        builder.setView(inputHolders);
        builder.setPositiveButton(R.string.ok_str, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (editingNote) { //if we're editing a note, just update the contents
                    ((UmlNoteNode) selected).setText(inputTextView.getText().toString());
                } else {
                    selected = new UmlNoteNode(inputTextView.getText().toString(), 100, 100);

                    Log.i(TAG, "Creating note: " + selected);

                    allClassDrawables.add(selected);
                }

                savePending = true; //we've made changes to the editor
                postInvalidate();
            }
        });
        builder.setNegativeButton(R.string.cancel_str, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show(); //show the AlertDialog
    }

    /**
     * Add a UmlDrawable to this View
     *
     * @param drawable to add to this View
     */
    public void addDrawable(UmlDrawable drawable) {
        allClassDrawables.add(drawable);
        postInvalidate();
    }


    /**
     * @return true if this working area is empty, false otherwise
     */
    public boolean isEmpty() {
        return this.allClassDrawables.isEmpty();
    }

    /**
     * @return an ArrayList contianing all the drawables
     */
    public ArrayList<UmlDrawable> getAllClassDrawables() {
        return allClassDrawables;
    }

    /**
     * Get whether this View is waiting for an arrow input
     *
     * @return waitingForArrowInput
     */
    public boolean isWaitingForArrowInput() {
        return waitingForArrowInput;
    }

    /**
     * set this View's waitingForArrowInput
     *
     * @param waitingForArrowInput new value to set
     */
    public void setWaitingForArrowInput(boolean waitingForArrowInput) {
        this.waitingForArrowInput = waitingForArrowInput;
    }

    /**
     * @return the UmlDrawable that is currently selected, null if nothing is selected
     */
    public UmlDrawable getSelected() {
        return selected;
    }

    /**
     * Get the object that the user tapped if waiting for an object
     *
     * @return UmlNode that the user tapped
     */
    public UmlNode getNewArrowHelper() {
        return newArrowHelper;
    }

    /**
     * @return a JSONObject that contains all the information of this editor
     */
    public JSONObject toJson() {
        try {
            JSONArray arr = new JSONArray();
            for (UmlDrawable drawable : allClassDrawables)
                arr.put(drawable.toJson());

            JSONObject obj = new JSONObject();
            obj.put(FileHelper.FILE_TYPE_KEY, FILE_TYPE);
            obj.put(ITEMS_KEY, arr);

            return obj;

        } catch (Exception e) {
            Log.e(TAG, "toArray: ", e);
            Toast.makeText(ctx, R.string.save_error, Toast.LENGTH_LONG).show();
            return null;
        }
    }

    /**
     * @return true if there is a change pending to be saved, false otherwise
     */
    public boolean getSavePending() {
        return savePending;
    }

    /**
     * @param savePending new boolean whether change is pending
     */
    public void setSavePending(boolean savePending) {
        this.savePending = savePending;
    }

    /**
     * Removes all items and "clears the working space"
     * THIS SHOULD ONLY BE CALLED AFTER USER'S CONSENT
     */
    public void resetSpace() {
        allClassDrawables.clear();
        selected = null;

        savePending = false;
        postInvalidate();
    }

    /**
     * Deletes the currently selected item
     */
    public void deleteItem() {
        if (selected != null) {
            //if the selected item is a UmlNode, it might have an arrow pointing to it
            if (selected instanceof UmlNode) {
                Iterator<UmlDrawable> iterator = allClassDrawables.iterator();
                //check all the items and see if there's an arrow pointing to the item we just removed
                while (iterator.hasNext()) {
                    UmlDrawable drawable = iterator.next();
                    if (drawable instanceof UmlBentArrow) {
                        UmlBentArrow arrow = (UmlBentArrow) drawable;
                        if (arrow.reliesOn(selected)) iterator.remove();
                    }
                }
            }
            allClassDrawables.remove(selected);
            savePending = true; //if an item is deleted, a save is pending
            selected = null; //now nothin
            // g is selected
            draggable = false;
        }

        postInvalidate();
    }

    /**
     * @return a Bitmap object containing this View's items
     */
    public Bitmap getBitmap() {
        UmlDrawable selectedTemp = null;
        if (selected != null) {
            selectedTemp = selected;
            selected = null;
        }
        Bitmap result = Bitmap.createBitmap(this.getWidth(), this.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Drawable bgDrawable = this.getBackground();
        if (bgDrawable != null)
            bgDrawable.draw(canvas);
        else
            canvas.drawColor(Color.WHITE);
        this.draw(canvas);
        selected = selectedTemp;
        return result;
    }

}