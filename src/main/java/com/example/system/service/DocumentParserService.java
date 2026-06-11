package com.example.system.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentParserService {

    public String parse(MultipartFile file) {
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (extension == null) {
            throw new IllegalArgumentException("仅支持上传 PDF 或 TXT 文件");
        }

        try {
            return switch (extension.toLowerCase(Locale.ROOT)) {
                case "pdf" -> normalize(parsePdf(file.getBytes()));
                case "txt" -> normalize(parseText(file.getBytes()));
                default -> throw new IllegalArgumentException("仅支持上传 PDF 或 TXT 文件");
            };
        } catch (IOException ex) {
            throw new IllegalArgumentException("文档解析失败，请确认文件内容可读取", ex);
        }
    }

    private String parsePdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String parseText(byte[] bytes) throws CharacterCodingException {
        try {
            return decode(bytes, StandardCharsets.UTF_8);
        } catch (CharacterCodingException ex) {
            return decode(bytes, Charset.forName("GB18030"));
        }
    }

    private String decode(byte[] bytes, Charset charset) throws CharacterCodingException {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private String normalize(String text) {
        return text.replace('\u0000', ' ')
                .replaceAll("\\r\\n?", "\n")
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
