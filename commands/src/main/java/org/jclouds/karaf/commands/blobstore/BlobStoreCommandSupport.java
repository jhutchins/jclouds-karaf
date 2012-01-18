/**
 * Copyright (C) 2011, the original authors
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */
package org.jclouds.karaf.commands.blobstore;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.felix.gogo.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.compute.ComputeService;
import org.jclouds.karaf.utils.blobstore.BlobStoreHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author iocanel
 */
public abstract class BlobStoreCommandSupport extends OsgiCommandSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlobStoreCommandSupport.class);
    private static final int SIZE = 32 * 1024;

    public static final String PROVIDERFORMAT = "%-16s %s";

    private List<BlobStore> services;
    private Set<String> containerCache;
    private Set<String> blobCache;

    @Option(name = "--provider")
    protected String provider;

    public void setBlobStoreServices(List<BlobStore> services) {
        this.services = services;
    }

    protected List<BlobStore> getBlobStoreServices() {
        if (provider == null) {
            return services;
        } else {
            return Collections.singletonList(getBlobStore());
        }
    }

    protected BlobStore getBlobStore() {
        return BlobStoreHelper.getBlobStore(provider, services);
    }

    /**
     * Reads an Object from the blob store.
     *
     * @param containerName
     * @param blobName
     * @return
     */
    public Object read(String containerName, String blobName) {
        Object result = null;
        ObjectInputStream ois = null;

        BlobStore blobStore = getBlobStore();
        blobStore.createContainerInLocation(null, containerName);

        InputStream is = blobStore.getBlob(containerName, blobName).getPayload().getInput();

        try {
            ois = new ObjectInputStream(is);
            result = ois.readObject();
        } catch (IOException e) {
            LOGGER.error("Error reading object.", e);
        } catch (ClassNotFoundException e) {
            LOGGER.error("Error reading object.", e);
        } finally {
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e) {
                }
            }

            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }
        return result;
    }


    /**
     * Returns an InputStream to a {@link Blob}.
     * @param containerName
     * @param blobName
     * @return
     */
    public InputStream getBlobInputStream(String containerName, String blobName) {
      return  getBlobStore().getBlob(containerName, blobName).getPayload().getInput();
    }

    /**
     * Writes to the {@link Blob} by serializing an Object.
     * @param containerName
     * @param blobName
     * @param object
     */
    public void write(String containerName, String blobName, Object object) {
        BlobStore blobStore = getBlobStore();
        Blob blob = blobStore.blobBuilder(blobName).build();
        blob.setPayload(toBytes(object));
        blobStore.putBlob(containerName, blob);
    }

    /**
     * Writes to the {@link Blob} using an InputStream.
     * @param bucket
     * @param blobName
     * @param is
     */
    public void write(String bucket, String blobName, InputStream is) {
        BlobStore blobStore = getBlobStore();
        Blob blob = blobStore.blobBuilder(blobName).build();
        blob.setPayload(is);
        blobStore.putBlob(bucket, blob);
        try {
            is.close();
        } catch (Exception ex) {
            LOGGER.warn("Error closing input stream.", ex);
        }
    }

    public byte[] toBytes(Object object) {
        byte[] result = null;

        if (object instanceof byte[]) {
            return (byte[]) object;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = null;

        try {
            oos = new ObjectOutputStream(baos);
            oos.writeObject(object);
            result = baos.toByteArray();
        } catch (IOException e) {
            LOGGER.error("Error while writing blob", e);
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException e) {
                }
            }

            if (baos != null) {
                try {
                    baos.close();
                } catch (IOException e) {
                }
            }
        }
        return result;
    }

    /**
     * Reads a bye[] from a URL.
     *
     * @param url
     * @return
     */
    public byte[] readFromUrl(URL url) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataInputStream dis = null;
        try {
            dis = new DataInputStream(url.openStream());
            int size = 0;
            while ((size = dis.available()) > 0) {
                byte[] buffer = new byte[size];
                baos.write(buffer);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            LOGGER.warn("Failed to read from stream.", e);
        } finally {
            if (dis != null) {
                try {
                    dis.close();
                } catch (Exception e) {
                    //Ignore
                }
            }

            if (baos != null) {
                try {
                    baos.close();
                } catch (Exception e) {
                    //Ignore
                }
            }

        }
        return new byte[0];
    }


    /**
     * Copies data from {@link InputStream} to {@link OutputStream}.
     *
     * @param is Source {@link InputStream}.
     * @param os Target {@link OutputStream}.
     */
    public void copy(InputStream is, OutputStream os) {
        byte[] buffer = new byte[SIZE];
        int read = 0;
        try {
            read = is.read(buffer);
            while (read >= 0) {
                if (read > 0) {
                    os.write(buffer, 0, read);
                }
                read = is.read(buffer);
            }
        } catch (IOException e) {

        } finally {

            if (os != null) {
                try {
                    os.close();
                } catch (Exception ex) {
                    //Ignore
                }
            }

            if (is != null) {
                try {
                    is.close();
                } catch (Exception ex) {
                    //Ignore
                }
            }
        }
    }

     protected void printBlobStoreProviders(List<BlobStore> blobStores, String indent, PrintStream out) {
        out.println(String.format(PROVIDERFORMAT, "[id]", "[type]"));
        for (BlobStore blobStore : blobStores) {
            out.println(String.format(PROVIDERFORMAT, blobStore.getContext().getProviderSpecificContext().getId(), "blob"));
        }
    }

    public Set<String> getContainerCache() {
        return containerCache;
    }

    public void setContainerCache(Set<String> containerCache) {
        this.containerCache = containerCache;
    }

    public Set<String> getBlobCache() {
        return blobCache;
    }

    public void setBlobCache(Set<String> blobCache) {
        this.blobCache = blobCache;
    }
}
