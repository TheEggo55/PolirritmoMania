package polyrhythmmania.world.tileset

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.eclipsesource.json.JsonObject


/**
 * A [TilesetRegion] is a [TextureRegion] with other metadata for [TexturePack].
 *
 */
class TilesetRegion(val id: String, region: TextureRegion, val spacing: RegionSpacing)
    : TextureRegion(region) {

    companion object {
        fun create(id: String, region: TextureRegion?, spacing: RegionSpacing = RegionSpacing.ZERO): TilesetRegion? {
            if (region == null) return null
            return TilesetRegion(id, region, spacing)
        }
    }

}

/**
 * Extra spacing for certain sprites to improve rendering that isn't normally available in the GBA sprites.
 */
data class RegionSpacing(val spacing: Int, val normalWidth: Int, val normalHeight: Int) {
    companion object {
        val ZERO: RegionSpacing = RegionSpacing(0, 0, 0)
        
        fun readJson(obj: JsonObject): RegionSpacing {
            return RegionSpacing(obj.getInt("s", 0), obj.getInt("nw", 0), obj.getInt("nh", 0))
        }
    }
    
    fun toJson(obj: JsonObject): JsonObject {
        obj.add("s", spacing)
        obj.add("nw", normalWidth)
        obj.add("nh", normalHeight)
        
        return obj
    }
}
