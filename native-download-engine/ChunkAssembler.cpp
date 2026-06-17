#include <string>
#include <vector>
#include <fstream>
#include <iostream>

class ChunkAssembler {
public:
    /**
     * Natively assembles separate chunk files into a single output file with high-speed buffered block-reads.
     */
    static bool assemble(const std::vector<std::string>& chunks, const std::string& output) {
        std::ofstream out(output, std::ios::binary | std::ios::trunc);
        if (!out.is_open()) {
            std::cerr << "Orion native merge: Failed to open output destination: " << output << std::endl;
            return false;
        }

        // Use a large 1MB buffer for high throughput file block merging
        const size_t bufferSize = 1024 * 1024;
        std::vector<char> buffer(bufferSize);

        for (const auto& chunk : chunks) {
            std::ifstream in(chunk, std::ios::binary);
            if (!in.is_open()) {
                std::cerr << "Orion native merge: Failed to open chunk segment source: " << chunk << std::endl;
                out.close();
                return false;
            }

            while (in) {
                in.read(buffer.data(), bufferSize);
                std::streamsize bytesRead = in.gcount();
                if (bytesRead > 0) {
                    out.write(buffer.data(), bytesRead);
                }
            }
            in.close();
        }

        out.close();
        return true;
    }
};
