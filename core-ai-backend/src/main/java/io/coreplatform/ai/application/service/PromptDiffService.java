package io.coreplatform.ai.application.service;

import io.coreplatform.ai.application.domain.PromptDiffLine;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PromptDiffService {

    public List<PromptDiffLine> compare(String section, String left, String right) {
        String[] a = lines(left);
        String[] b = lines(right);
        if ((long) a.length * b.length > 250_000L) {
            return positional(section, a, b);
        }
        int[][] lcs = new int[a.length + 1][b.length + 1];
        for (int i = a.length - 1; i >= 0; i--) {
            for (int j = b.length - 1; j >= 0; j--) {
                lcs[i][j] = a[i].equals(b[j])
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<PromptDiffLine> result = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < a.length || j < b.length) {
            if (i < a.length && j < b.length && a[i].equals(b[j])) {
                result.add(new PromptDiffLine(section, "SAME", i + 1, j + 1, a[i]));
                i++;
                j++;
            } else if (j < b.length && (i == a.length || lcs[i][j + 1] >= lcs[i + 1][j])) {
                result.add(new PromptDiffLine(section, "ADDED", 0, j + 1, b[j++]));
            } else {
                result.add(new PromptDiffLine(section, "REMOVED", i + 1, 0, a[i++]));
            }
        }
        return List.copyOf(result);
    }

    private List<PromptDiffLine> positional(String section, String[] left, String[] right) {
        List<PromptDiffLine> result = new ArrayList<>();
        int count = Math.max(left.length, right.length);
        for (int i = 0; i < count; i++) {
            if (i < left.length && i < right.length && left[i].equals(right[i])) {
                result.add(new PromptDiffLine(section, "SAME", i + 1, i + 1, left[i]));
            } else {
                if (i < left.length) {
                    result.add(new PromptDiffLine(section, "REMOVED", i + 1, 0, left[i]));
                }
                if (i < right.length) {
                    result.add(new PromptDiffLine(section, "ADDED", 0, i + 1, right[i]));
                }
            }
        }
        return List.copyOf(result);
    }

    private String[] lines(String value) {
        return (value == null ? "" : value).split("\\R", -1);
    }
}
