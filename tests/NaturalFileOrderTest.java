package com.ruih.framedeck;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class NaturalFileOrderTest {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("check " + checks);
    }
    private static void order(String[] input, String... expected) {
        Arrays.sort(input, NaturalFileOrder::compare);
        check(Arrays.equals(input, expected));
    }
    public static void main(String[] args) {
        // Reproduce the screenshot: ZIP directory order is 1, 10, 100...109, 11...
        List<String> screenshot = new ArrayList<>();
        for (int i = 1; i <= 116; i++) screenshot.add(i + ".mp4");
        Collections.sort(screenshot);
        check(screenshot.get(1).equals("10.mp4"));
        screenshot.sort(NaturalFileOrder::compare);
        for (int i = 1; i <= 116; i++) check(screenshot.get(i - 1).equals(i + ".mp4"));

        order(new String[]{"第10集.mp4", "第2集.mp4", "第1集.mp4"},
                "第1集.mp4", "第2集.mp4", "第10集.mp4");
        order(new String[]{"001.mp4", "01.mp4", "1.mp4", "000.mp4", "0.mp4"},
                "0.mp4", "000.mp4", "1.mp4", "01.mp4", "001.mp4");
        order(new String[]{"part2-10.mp4", "part10-1.mp4", "part2-2.mp4"},
                "part2-2.mp4", "part2-10.mp4", "part10-1.mp4");
        order(new String[]{"02-b.mp4", "2-a.mp4", "2-b.mp4"},
                "2-a.mp4", "2-b.mp4", "02-b.mp4");
        order(new String[]{"VIDEO10.MP4", "video2.mp4", "Video1.mp4"},
                "Video1.mp4", "video2.mp4", "VIDEO10.MP4");
        order(new String[]{"folder10/1.mp4", "folder2/10.mp4", "folder2/2.mp4"},
                "folder2/2.mp4", "folder2/10.mp4", "folder10/1.mp4");
        check(NaturalFileOrder.compare("a\\2.mp4", "a/10.mp4") < 0);
        check(NaturalFileOrder.compare("99999999999999999999999.mp4", "100000000000000000000000.mp4") < 0);
        check(NaturalFileOrder.compare("same.mp4", "same.mp4") == 0);

        // Sort complete entries, so selecting an item retains its matching byte offset.
        List<ZipArchive.Entry> entries = new ArrayList<>();
        for (int i : new int[]{100, 10, 1, 2})
            entries.add(new ZipArchive.Entry(i + ".mp4", 0, 0, 20, 20, 0, i * 10L, i * 10L + 30));
        entries.sort((a, b) -> NaturalFileOrder.compare(a.name, b.name));
        for (int i = 0; i < 4; i++) check(entries.get(i).localOffset == new long[]{10, 20, 100, 1000}[i]);

        String[] samples = {"a0.mp4", "a00.mp4", "a1.mp4", "a01.mp4", "a1a.mp4", "a01b.mp4",
                "A1b.mp4", "a2.mp4", "a10.mp4", "a10a.mp4", "a100.mp4", "b1.mp4", "", "a"};
        boolean contract = true;
        for (String a : samples) for (String b : samples) {
            contract &= Integer.signum(NaturalFileOrder.compare(a, b)) == -Integer.signum(NaturalFileOrder.compare(b, a));
            for (String c : samples)
                if (NaturalFileOrder.compare(a, b) <= 0 && NaturalFileOrder.compare(b, c) <= 0)
                    contract &= NaturalFileOrder.compare(a, c) <= 0;
        }
        check(contract);
        System.out.println("Passed " + checks + " natural-sort checks, including all 116 screenshot filenames.");
    }
}
