package com.example.sudokusolvermachinelearning;

import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class MainActivity extends AppCompatActivity {

    private Interpreter tflite;
    private EditText[][] sudokuCells;
    private GridLayout sudokuGrid;
    private TextView statusText;
    private Button btnSolve, btnClear, btnLoadExample;

    // Example puzzle (0 represents empty cells)
    private int[][] examplePuzzle = {
            {5, 3, 0, 0, 7, 0, 0, 0, 0},
            {6, 0, 0, 1, 9, 5, 0, 0, 0},
            {0, 9, 8, 0, 0, 0, 0, 6, 0},
            {8, 0, 0, 0, 6, 0, 0, 0, 3},
            {4, 0, 0, 8, 0, 3, 0, 0, 1},
            {7, 0, 0, 0, 2, 0, 0, 0, 6},
            {0, 6, 0, 0, 0, 0, 2, 8, 0},
            {0, 0, 0, 4, 1, 9, 0, 0, 5},
            {0, 0, 0, 0, 8, 0, 0, 7, 9}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initializeViews();
        initializeModel();
        setupSudokuGrid();
        setupButtonListeners();
    }

    private void initializeViews() {
        sudokuGrid = findViewById(R.id.sudokuGrid);
        statusText = findViewById(R.id.statusText);
        btnSolve = findViewById(R.id.btnSolve);
        btnClear = findViewById(R.id.btnClear);
        btnLoadExample = findViewById(R.id.btnLoadExample);
    }

    private void initializeModel() {
        try {
            tflite = new Interpreter(loadModelFile());
            statusText.setText("Model loaded successfully!");
        } catch (Exception e) {
            statusText.setText("Error loading model: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd("sudoku_model.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void setupSudokuGrid() {
        sudokuCells = new EditText[9][9];

        sudokuGrid.post(() -> {
            int gridSize = sudokuGrid.getWidth(); // same as height due to 1:1 ratio
            int cellSize = gridSize / 9;
            int borderPx = dpToPx(0.5f);

            for (int i = 0; i < 9; i++) {
                for (int j = 0; j < 9; j++) {
                    EditText cell = new AppCompatEditText(this);

                    GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                    params.width = cellSize;
                    params.height = cellSize;
                    params.setMargins(borderPx, borderPx, borderPx, borderPx);

                    cell.setLayoutParams(params);
                    cell.setBackgroundResource(R.drawable.sudoku_cell_border);
                    cell.setTextSize(20);
                    cell.setGravity(Gravity.CENTER);
                    cell.setIncludeFontPadding(false);
                    cell.setLineSpacing(0, 1.2f);
                    cell.setInputType(InputType.TYPE_CLASS_NUMBER);
                    cell.setFilters(new InputFilter[]{
                            new InputFilter.LengthFilter(1),
                            (source, start, end, dest, dstart, dend) -> {
                                if (source.toString().matches("[1-9]") || source.toString().isEmpty()) {
                                    return null;
                                }
                                return "";
                            }
                    });

                    sudokuCells[i][j] = cell;
                    sudokuGrid.addView(cell);
                }
            }
        });
    }

    // Convert dp to px (float version)
    private int dpToPx(float dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void setupButtonListeners() {
        btnLoadExample.setOnClickListener(v -> loadExamplePuzzle());
        btnSolve.setOnClickListener(v -> solvePuzzle());
        btnClear.setOnClickListener(v -> clearGrid());
    }

    private void loadExamplePuzzle() {
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (examplePuzzle[i][j] == 0) {
                    sudokuCells[i][j].setText("");
                } else {
                    sudokuCells[i][j].setText(String.valueOf(examplePuzzle[i][j]));
                }
            }
        }
        statusText.setText("Example puzzle loaded!");
    }

    private void solvePuzzle() {
        if (tflite == null) {
            Toast.makeText(this, "Model not loaded!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            float[][][][] input = new float[1][9][9][1];

            for (int i = 0; i < 9; i++) {
                for (int j = 0; j < 9; j++) {
                    String cellText = sudokuCells[i][j].getText().toString().trim();
                    if (cellText.isEmpty()) {
                        input[0][i][j][0] = 0.0f;
                    } else {
                        try {
                            int value = Integer.parseInt(cellText);
                            if (value >= 1 && value <= 9) {
                                input[0][i][j][0] = value / 9.0f; // normalize
                            } else {
                                input[0][i][j][0] = 0.0f;
                            }
                        } catch (NumberFormatException e) {
                            input[0][i][j][0] = 0.0f;
                        }
                    }
                }
            }

            float[][][][] output = new float[1][9][9][9];
            tflite.run(input, output);

            for (int i = 0; i < 9; i++) {
                for (int j = 0; j < 9; j++) {
                    int maxIndex = 0;
                    float maxProb = output[0][i][j][0];
                    for (int k = 1; k < 9; k++) {
                        if (output[0][i][j][k] > maxProb) {
                            maxProb = output[0][i][j][k];
                            maxIndex = k;
                        }
                    }
                    sudokuCells[i][j].setText(String.valueOf(maxIndex + 1));
                }
            }

            statusText.setText("Puzzle solved!");
        } catch (Exception e) {
            statusText.setText("Error solving puzzle: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void clearGrid() {
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                sudokuCells[i][j].setText("");
            }
        }
        statusText.setText("Grid cleared! Enter a puzzle to solve.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tflite != null) {
            tflite.close();
        }
    }
}
