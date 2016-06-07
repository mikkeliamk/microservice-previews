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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Hashtable;

import javax.imageio.ImageIO;
import net.xeoh.plugins.base.annotations.Capabilities;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.imgscalr.Scalr;

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
        String state = "error";
        String output = "";
        String importDir = "";
        String uploadDir = "";
        String ingestDir = "";
        String failedDir = "";
        String filename = "";
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
            if (options.containsKey("faileddirectory")) {
                failedDir = options.get("faileddirectory").toString();
            }
            if (options.containsKey("filename")) {
                filename = options.get("filename").toString();
            }
            if (options.containsKey("organization")) {
                organization = options.get("organization").toString();
            }
            if (options.containsKey("username")) {
                user = options.get("username").toString();
            }
        }

        String filePath = importDir;
        String thumbPath = uploadDir;
        
        try {
                   
            // create thumb
            output += createThumbFile(filePath, thumbPath, filename);

            // move to ingest directory
            output += moveToIngestDirectory(filename, importDir, ingestDir);
            
            state = "completed";
            success = true;
       
        } catch (Exception e) {
            output += "Creating thumbnail for "+filename+" failed.\n"+e.toString()+"\n";
        }
        
        if (!success) {
            // if exception, move imported file to failed directory
            File file = new File(importDir + filename);
            File failedfile = findFileName(failedDir, filename);
                        
            if (!failedfile.getParentFile().exists()) {
                failedfile.getParentFile().mkdirs();
            }
            
            FileUtils.moveFile(file, failedfile);
        }
              
        super.setState(state);
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
    private String createThumbFile(String filePath, String thumbPath, String fileName) throws Exception, IOException {
        String fileExtension                = FilenameUtils.getExtension(fileName);
        String baseName                     = FilenameUtils.removeExtension(fileName);
        File origFile                       = null;
        File thumbFile                      = null;
        BufferedImage bufferedImage         = null;
        BufferedImage thumb                 = null;
        String thumbnailPathAndName         = "";
        FileOutputStream fileOutputStream   = null;
        String output = "";
               
        String thumbnailName = THUMB_DSID + baseName + "."+THUMB_EXTENSION;
              
        thumbFile = new File(thumbPath + thumbnailName);
        origFile = new File(filePath + fileName);
        
        if (thumbFile.exists()) {
            output += "Cannot create thumbnail for "+fileName+". File already exists.\n";
            throw new Exception(output);
        }
        
        fileOutputStream = new FileOutputStream(thumbFile.toString()); 
            
        if (fileExtension.equalsIgnoreCase("pdf")) {
            try {
                // open the PDF file
                PDDocument document = PDDocument.load(origFile.toString());
                                                
                PDPage firstPage = (PDPage) document.getDocumentCatalog().getAllPages().get(0);
                // get page 1 as an image
                bufferedImage = firstPage.convertToImage();
                thumb = Scalr.resize(bufferedImage, 250);
                
                ImageIO.write(thumb, THUMB_EXTENSION, fileOutputStream);
                
                // close the pdf file
                document.close();
                
            } catch (IOException e) {
                output += "Error creating thumbnail for pdf file: "+e.getMessage();
                throw new Exception(output);
            }
            
        } else  if (fileExtension.equalsIgnoreCase("tif")
                    || fileExtension.equalsIgnoreCase("jpg")
                    || fileExtension.equalsIgnoreCase("jpeg")
                    || fileExtension.equalsIgnoreCase("jpf")
                    || fileExtension.equalsIgnoreCase("jp2")
                    || fileExtension.equalsIgnoreCase("gif")
                    || fileExtension.equalsIgnoreCase("png")) {
            
            // Decide if or not try to make an animated gif thumb for animated gif files
            // At the moment it does not and current solution (Scalr) does not support making animated gifs
            if (fileExtension.equalsIgnoreCase("tif")) {
                SeekableStream s = new FileSeekableStream(origFile.toString());
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
                ImageIO.write(thumb, THUMB_EXTENSION, fileOutputStream);
                
            } else {
                
                bufferedImage = ImageIO.read(new File(origFile.toString()));
                if (bufferedImage != null) {
                    thumb = Scalr.resize(bufferedImage, 200); // Scale the original image around the width and height of 150 px, maintaining original aspect ratio
                    ImageIO.write(thumb, THUMB_EXTENSION, fileOutputStream);
                    
                } else {
                    output += "Cannot create thumbnail for "+fileName+"\n";
                    throw new Exception(output);
                }
            }
                    
        } else {
            output += "Cannot create thumbnail for "+fileName+"\n";
            throw new Exception(output);
        }
               
        if (thumbFile.canRead()) {
            fileOutputStream.close();
            output += "Preview generated for "+fileName+"\n";

        } else {
            output += "Error creating thumbnail file for "+fileName+"\n";
            throw new Exception(output);
        }
        
        return output;
    }

    private String moveToIngestDirectory(String fileName, String importDirectory, String ingestDirectory) throws Exception {
        
        File file = new File(importDirectory + fileName);
        File targetfile = new File(ingestDirectory + fileName);
        String output = "";
        
        if (!targetfile.getParentFile().exists()) {
            targetfile.getParentFile().mkdirs();
        }
        
        if (targetfile.exists()) {
            output += "Cannot move "+fileName+". File already exists.\n";
            throw new Exception(output);
        }
        
        if (file.exists()) {
            FileUtils.moveFile(file, targetfile);
            output += "File "+fileName+" moved to ingestDirectory.\n";
            
        }
        
        return output;
    }
    
    /**
     * findFileName
     * @param dir              absolute path 
     * @param fileName         fileName
     * @return                 filename with the running nr of copies if file already exists in dir
     */   
    private File findFileName(String dir, String fileName) {
        
        File file = new File(dir + fileName);
        if (!file.exists()) {
            return file;
        }
               
        String baseName = FilenameUtils.removeExtension(fileName);
        String extension = FilenameUtils.getExtension(fileName);

        for (int i = 1; i < Integer.MAX_VALUE; i++) {
            Path path = Paths.get(dir, String.format("%s(%d).%s", baseName, i, extension));
            if (!Files.exists(path)) {
                return path.toFile();
            }
        }
        
        return file;
    }

}
