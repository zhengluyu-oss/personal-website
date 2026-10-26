package xyz.luyu.knowledge;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeFileParserTest {
    @TempDir Path directory;
    @Test void utf8ChineseTextKeepsLineLocations()throws Exception{
        Path text=directory.resolve("note.md");Files.writeString(text,"第一行\n第二行：中文知识\n");
        var rows=KnowledgeFileParser.text(text);
        assertEquals(1,rows.size());assertEquals(1,rows.get(0).fromLine());assertEquals(3,rows.get(0).toLine());
        assertTrue(rows.get(0).text().contains("中文知识"));
    }
    @Test void pdfExtractionRejectsPageOverrunAndBlankScan()throws Exception{
        Path file=directory.resolve("fixture.pdf");
        try(PDDocument document=new PDDocument()){
            var page=new PDPage();document.addPage(page);
            try(PDPageContentStream stream=new PDPageContentStream(document,page)){
                stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);
                stream.newLineAtOffset(40,700);stream.showText("Knowledge PDF extraction");stream.endText();
            }
            document.save(file.toFile());
        }
        var rows=KnowledgeFileParser.pdf(file,1);
        assertEquals(1,rows.size());assertEquals(1,rows.get(0).page());assertTrue(rows.get(0).text().contains("Knowledge PDF"));
        try(PDDocument document=new PDDocument()){document.addPage(new PDPage());document.addPage(new PDPage());document.save(file.toFile());}
        assertEquals("TooManyPages",assertThrows(RuntimeException.class,()->KnowledgeFileParser.pdf(file,1)).getClass().getSimpleName());
        assertEquals(List.of(),KnowledgeFileParser.pdf(file,2));
    }
    @Test void encryptedPdfIsClassifiedWithoutExposingContent()throws Exception{
        Path file=directory.resolve("locked.pdf");
        try(PDDocument document=new PDDocument()){
            document.addPage(new PDPage());document.protect(new StandardProtectionPolicy("owner","reader",new AccessPermission()));document.save(file.toFile());
        }
        assertEquals("Encrypted",assertThrows(RuntimeException.class,()->KnowledgeFileParser.pdf(file,1)).getClass().getSimpleName());
    }
    @Test void damagedPdfIsRejectedAndChineseTextKeepsItsPage()throws Exception{
        Path damaged=directory.resolve("damaged.pdf");Files.writeString(damaged,"%PDF-1.7\ntruncated");
        assertThrows(Exception.class,()->KnowledgeFileParser.pdf(damaged,1));
        Path font=Path.of("..","blog-frontend","luyu-blog","src","assets","woff","AlimamaFangYuanTiVF-Thin.ttf");
        assertTrue(Files.isRegularFile(font),"the repository's Chinese font fixture is required");
        Path chinese=directory.resolve("chinese.pdf");
        try(PDDocument document=new PDDocument()){
            PDType0Font embedded=PDType0Font.load(document,font.toFile());
            PDPage page=new PDPage();document.addPage(page);
            try(PDPageContentStream stream=new PDPageContentStream(document,page)){
                stream.beginText();stream.setFont(embedded,12);stream.newLineAtOffset(40,700);
                stream.showText("中文知识检索");stream.endText();
            }
            document.save(chinese.toFile());
        }
        var rows=KnowledgeFileParser.pdf(chinese,1);
        assertEquals(1,rows.size());assertEquals(1,rows.get(0).page());
        assertTrue(rows.get(0).text().contains("中文知识检索"));
    }
}
