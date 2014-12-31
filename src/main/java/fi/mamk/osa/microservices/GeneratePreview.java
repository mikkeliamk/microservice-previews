package fi.mamk.osa.microservices;

import com.belvain.soswe.workflow.Microservice;
import com.sun.media.jai.codec.FileSeekableStream;
import com.sun.media.jai.codec.ImageCodec;
import com.sun.media.jai.codec.ImageDecoder;
import com.sun.media.jai.codec.SeekableStream;
import com.sun.media.jai.codec.TIFFDecodeParam;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Hashtable;

import javax.imageio.ImageIO;
import net.xeoh.plugins.base.annotations.Capabilities;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.imgscalr.Scalr;
import org.jpedal.PdfDecoder;
import org.jpedal.exception.PdfException;
import org.jpedal.fonts.FontMappings;

@PluginImplementation
public class GeneratePreview extends Microservice {
    private static final String THUMB_DSID       = "thumb_";
    private static final String THUMB_EXTENSION  = "png";
    
    @Capabilities
    public String[] caps() {
        return new String[] {"name:GeneratePreview"};
    }

    @Override
    public boolean execute(String input, HashMap<String, Object> options)
            throws Exception {
        boolean success = false;
        String output = "";
        String importDir = "";
        String uploadDir = "";
        String ingestDir = "";
        String filename = "";
        String format = "";
        String organization = "";
        String user = "";
        
        if (options != null) {
            if (options.containsKey("importdirectory")) {
                importDir = options.get("importdirectory").toString();
            }
            if (options.containsKey("uploaddirectory")) {
                uploadDir = options.get("uploaddirectory").toString();
            }
            if (options.containsKey("ingestdirectory")) {
                ingestDir = options.get("ingestdirectory").toString();
            }
            if (options.containsKey("filename")) {
                filename = options.get("filename").toString();
            }
            if (options.containsKey("type")) {
                format = options.get("type").toString();
            }
            if (options.containsKey("organization")) {
                organization = options.get("organization").toString();
            }
            if (options.containsKey("username")) {
                user = options.get("username").toString();
            }
        }

        String filePath = importDir + filename;
        String thumbPath = uploadDir + filename;
        output += createThumbFile(filePath, format, thumbPath);
        
        // move to ingest directory
        output += moveToIngestDirectory(filename, importDir, ingestDir, output);

        success = true;
        super.setState("completed");
        super.setOutput(output);
        super.setCompleted(true);
        
        String log = super.getLog().replace("{organization}", organization).replace("{user}", user);
        super.setLog(log);
        log();
        
        return success;    
    }

    /**
     * createPreview for documents
     * @param filePath              pdfPathAndFileName, absolute path 
     * @param format                object format
     * @param thumbPath             previewPathAndFileName, absolute path
     * @throws IOException
     * @throws PdfException
     */
    private String createThumbFile(String filePath, String format, String thumbPath) {
        String fileExtension                = filePath.substring(filePath.lastIndexOf('.'), filePath.length());
        String fileName                     = filePath.substring(filePath.lastIndexOf('/')+1, filePath.lastIndexOf('.'));
        PdfDecoder pdfDecoder               = null;
        File pdfFile                        = null;
        File thumbFile                      = null;
        BufferedImage bufferedImage         = null;
        BufferedImage thumb                 = null;
        String thumbnailPathAndName         = "";
        FileOutputStream fileOutputStream   = null;
        String output = "";
        
        // use the same path, but change the extension to png
        thumbnailPathAndName = FilenameUtils.removeExtension(thumbPath)+"."+THUMB_EXTENSION;
        thumbnailPathAndName = thumbnailPathAndName.replace(fileName, THUMB_DSID+fileName);
        
        thumbFile = new File(thumbnailPathAndName);
        pdfFile = new File(filePath);
        if (thumbFile.exists() || !pdfFile.exists()) {
            return output;
        }
        
        if (fileExtension.equalsIgnoreCase(".pdf")) {
            pdfDecoder = new PdfDecoder(true);
            try {
                // set mappings for non-embedded fonts to use
                FontMappings.setFontReplacements();
        
                // open the PDF file
                pdfDecoder.openPdfFile(filePath);
                /**get page 1 as an image*/
                bufferedImage = pdfDecoder.getPageAsImage(1);
                thumb = Scalr.resize(bufferedImage, 250);
                fileOutputStream = new FileOutputStream(thumbnailPathAndName);
                ImageIO.write(thumb, THUMB_EXTENSION, fileOutputStream);
                // close the pdf file
                pdfDecoder.closePdfFile();            
                
            } catch (IOException e) {
                output += "Error creating thumbnail for "+FilenameUtils.getName(filePath)+": "+e.getMessage()+"\n";
            } catch (PdfException e) {
                output += "Error creating thumbnail for "+FilenameUtils.getName(filePath)+": "+e.getMessage()+"\n";
            }
            
        } else  if (fileExtension.equalsIgnoreCase(".tif")
                    || fileExtension.equalsIgnoreCase(".jpg")
                    || fileExtension.equalsIgnoreCase(".gif")
                    || fileExtension.equalsIgnoreCase(".png")) {
            
            try {
                // Decide if or not try to make an animated gif thumb for animated gif files
                // At the moment it does not and current solution (Scalr) does not support making animated gifs

                if (fileExtension.equalsIgnoreCase(".tif")) {
                    SeekableStream s = new FileSeekableStream(filePath);
                    TIFFDecodeParam param = null;
                    ImageDecoder dec = ImageCodec.createImageDecoder("tiff", s, param);
                    RenderedImage op = dec.decodeAsRenderedImage(0);
                    // RenderedImage to BufferedImage
                    ColorModel cm = op.getColorModel();
                    int width = op.getWidth();
                    int height = op.getHeight();
                    WritableRaster raster = cm.createCompatibleWritableRaster(width, height);
                    boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
                    Hashtable properties = new Hashtable();
                    String[] keys = op.getPropertyNames();
                    if (keys!=null) {
                        for (int i = 0; i < keys.length; i++) {
                            properties.put(keys[i], op.getProperty(keys[i]));
                        }
                    }
                    bufferedImage = new BufferedImage(cm, raster, isAlphaPremultiplied, properties);
                    op.copyData(raster);
                    
                    thumb = Scalr.resize(bufferedImage, 200); // Scale the original image around the width and height of 150 px, maintaining original aspect ratio
                    fileOutputStream = new FileOutputStream(thumbnailPathAndName);
                    ImageIO.write(thumb, THUMB_EXTENSION, fileOutputStream);
                    
                } else {
                    bufferedImage = ImageIO.read(new File(filePath));
                    thumb = Scalr.resize(bufferedImage, 200); // Scale the original image around the width and height of 150 px, maintaining original aspect ratio
                    fileOutputStream = new FileOutputStream(thumbnailPathAndName);
                    ImageIO.write(thumb, THUMB_EXTENSION, fileOutputStream);

                }
                
            } catch (IOException e) {
                output += "Error creating thumbnail for "+FilenameUtils.getName(filePath)+": "+e.getMessage()+"\n";
            }
            
        } else {
            output += "Cannot create thumbnail for "+FilenameUtils.getName(filePath)+"\n";
        }
        
        if (thumbFile.canRead()) {
            try {
                fileOutputStream.close();
            } catch (IOException e) {
                output += "Error creating thumbnail file: "+e.getMessage()+"\n";
            }
        }
        
        output += "Preview generated for "+FilenameUtils.getName(filePath)+"\n";
        
        return output;
    }

    public String moveToIngestDirectory(String fileName, String importDirectory, String ingestDirectory, String output) {

        File file = new File(importDirectory + fileName);
        File targetfile = new File(ingestDirectory + file.getName());

        if (!targetfile.getParentFile().exists()) {
            targetfile.getParentFile().mkdirs();
        }

        try {
            FileUtils.moveFile(file, targetfile);
            output += "File "+fileName+" moved to ingestDirectory.\n";

        } catch (IOException e) {
            output += "moveToIngestDirectory error, "+e+"\n";
        }
        return output;
    }

}
