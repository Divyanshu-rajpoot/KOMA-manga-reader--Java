package app.koma.library;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileNameParserTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            // file name                                              | series            | number | end  | kind
            "Paper Ronin v07.cbz                                      | Paper Ronin       | 7      |      | volume",
            "Paper Ronin v7.cbz                                       | Paper Ronin       | 7      |      | volume",
            "Paper Ronin Vol. 7.cbz                                   | Paper Ronin       | 7      |      | volume",
            "Paper Ronin Vol 7.cbz                                    | Paper Ronin       | 7      |      | volume",
            "Paper Ronin Volume 12.cbz                                | Paper Ronin       | 12     |      | volume",
            "Paper Ronin - Volume 03.cbz                              | Paper Ronin       | 3      |      | volume",
            "Paper_Ronin_v03.cbz                                      | Paper Ronin       | 3      |      | volume",
            "[Group] Paper Ronin v03 (2019) (Digital).cbz             | Paper Ronin       | 3      |      | volume",
            "Paper Ronin v03 (Digital) [HQ] {1080p}.cbz               | Paper Ronin       | 3      |      | volume",
            "Ashen Tide v12.cbz                                       | Ashen Tide        | 12     |      | volume",
            "Ashen Tide v13.zip                                       | Ashen Tide        | 13     |      | volume",
            "Ember Ward Vol 04.cbz                                    | Ember Ward        | 4      |      | volume",
            "Night Market 47 v02.cbz                                  | Night Market 47   | 2      |      | volume",
            "Vagabond v01 - The Beginning.cbz                         | Vagabond          | 1      |      | volume",
            "Berserk, Vol. 41.cbz                                     | Berserk           | 41     |      | volume",
            "One-Punch Man v25.cbz                                    | One-Punch Man     | 25     |      | volume",
            "Tome 5 bis.cbz                                           |                   | 0      |      | volume",
            "Quiet Blade Omnibus 01-05.cbz                            | Quiet Blade       | 1      | 5    | omnibus",
            "Quiet Blade 01-05.cbz                                    | Quiet Blade       | 1      | 5    | omnibus",
            "Quiet Blade v01-05.cbz                                   | Quiet Blade       | 1      | 5    | omnibus",
            "Quiet Blade Vol. 1-3 (Omnibus).cbz                       | Quiet Blade       | 1      | 3    | omnibus",
            "Tidebreaker c012.cbz                                     | Tidebreaker       | 12     |      | chapter",
            "Tidebreaker Ch 12.cbz                                    | Tidebreaker       | 12     |      | chapter",
            "Tidebreaker Ch. 12.5.cbz                                 | Tidebreaker       | 12.5   |      | chapter",
            "Tidebreaker Chapter 101.cbz                              | Tidebreaker       | 101    |      | chapter",
            "Tidebreaker - Episode 7 [Scans].cbz                      | Tidebreaker       | 7      |      | chapter",
            "Solo Leveling c200 (2021).cbz                            | Solo Leveling     | 200    |      | chapter",
            "Crow Season 03.cbz                                       | Crow Season       | 3      |      | volume",
            "Crow Season #4.cbz                                       | Crow Season       | 4      |      | volume",
            "Crow Season (2019).cbz                                   |                   | 0      |      | volume",
            "Steel Koi 1999.cbz                                       |                   | 0      |      | volume",
            "chapter_bundle_final.cbz                                 |                   | 0      |      | volume",
            "scans.cbz                                                |                   | 0      |      | volume",
            "Salt.and.Static.v02.cbz                                  | Salt and Static   | 2      |      | volume",
            "Moonlit Court v3.CBZ                                     | Moonlit Court     | 3      |      | volume",
    })
    void parses(String file, String series, double number, Double end, String kind) {
        FileNameParser.Parsed p = FileNameParser.parse(file);
        if (series == null) {
            assertFalse(p.matched(), "should be unmatched: " + file + " → " + p);
            return;
        }
        assertTrue(p.matched(), "should match: " + file);
        assertEquals(series, p.series(), file);
        assertEquals(number, p.number(), 0.001, file);
        if (end == null) assertNull(p.numberEnd(), file);
        else assertEquals(end, p.numberEnd(), 0.001, file);
        assertEquals(kind, p.kind(), file);
    }
}
