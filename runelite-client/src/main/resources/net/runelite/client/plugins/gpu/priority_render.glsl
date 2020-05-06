/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

// Calculate adjusted priority for a face with a given priority, distance, and
// model global min10 and face distance averages. This allows positioning faces
// with priorities 10/11 into the correct 'slots' resulting in 18 possible
// adjusted priorities
int priority_map(int p, int distance, int _min10, int avg1, int avg2, int avg3) {
  // (10, 11)  0  1  2  (10, 11)  3  4  (10, 11)  5  6  7  8  9  (10, 11)
  //   0   1   2  3  4    5   6   7  8    9  10  11 12 13 14 15   16  17
  switch (p) {
    case 0: return 2;
    case 1: return 3;
    case 2: return 4;
    case 3: return 7;
    case 4: return 8;
    case 5: return 11;
    case 6: return 12;
    case 7: return 13;
    case 8: return 14;
    case 9: return 15;
    case 10:
      if (distance > avg1) {
        return 0;
      } else if (distance > avg2) {
        return 5;
      } else if (distance > avg3) {
        return 9;
      } else {
        return 16;
      }
    case 11:
      if (distance > avg1 && _min10 > avg1) {
        return 1;
      } else if (distance > avg2 && (_min10 > avg1 || _min10 > avg2)) {
        return 6;
      } else if (distance > avg3 && (_min10 > avg1 || _min10 > avg2 || _min10 > avg3)) {
        return 10;
      } else {
        return 17;
      }
    default:
      return -1;
  }
}

// calculate the number of faces with a lower adjusted priority than
// the given adjusted priority
int count_prio_offset(SHARED_ARGS int priority) {
  int total = 0;
  switch (priority) {
    case 17:
      total += SHARED(totalMappedNum)[16];
    case 16:
      total += SHARED(totalMappedNum)[15];
    case 15:
      total += SHARED(totalMappedNum)[14];
    case 14:
      total += SHARED(totalMappedNum)[13];
    case 13:
      total += SHARED(totalMappedNum)[12];
    case 12:
      total += SHARED(totalMappedNum)[11];
    case 11:
      total += SHARED(totalMappedNum)[10];
    case 10:
      total += SHARED(totalMappedNum)[9];
    case 9:
      total += SHARED(totalMappedNum)[8];
    case 8:
      total += SHARED(totalMappedNum)[7];
    case 7:
      total += SHARED(totalMappedNum)[6];
    case 6:
      total += SHARED(totalMappedNum)[5];
    case 5:
      total += SHARED(totalMappedNum)[4];
    case 4:
      total += SHARED(totalMappedNum)[3];
    case 3:
      total += SHARED(totalMappedNum)[2];
    case 2:
      total += SHARED(totalMappedNum)[1];
    case 1:
      total += SHARED(totalMappedNum)[0];
    case 0:
    default:
      return total;
  }
}

void get_face(GLOBAL_ARGS uint localId, modelinfo minfo,
    OUTARG(int, prio), OUTARG(int, dis), OUTARG(ivec4, o1), OUTARG(ivec4, o2), OUTARG(ivec4, o3)) {
  int size = minfo.size;

  if (localId < size) {
    int offset = minfo.offset;
    int flags = minfo.flags;
    int radius = (flags & 0x7fffffff) >> 12;
    int orientation = flags & 0x7ff;

    ivec4 thisA;
    ivec4 thisB;
    ivec4 thisC;

    // Grab triangle vertices from the correct buffer
    if (flags < 0) {
      thisA = vb[offset + localId * 3];
      thisB = vb[offset + localId * 3 + 1];
      thisC = vb[offset + localId * 3 + 2];
    } else {
      thisA = tempvb[offset + localId * 3];
      thisB = tempvb[offset + localId * 3 + 1];
      thisC = tempvb[offset + localId * 3 + 2];
    }

    // rotate for model orientation
    ivec4 thisrvA = rotate_int(PASS_GLOBALS thisA, orientation);
    ivec4 thisrvB = rotate_int(PASS_GLOBALS thisB, orientation);
    ivec4 thisrvC = rotate_int(PASS_GLOBALS thisC, orientation);

    // calculate distance to face
    int thisPriority = (thisA.w >> 16) & 0xff;// all vertices on the face have the same priority
    int thisDistance;
    if (radius == 0) {
      thisDistance = 0;
    } else {
      thisDistance = face_distance(thisrvA, thisrvB, thisrvC, UNIFORM(cameraYaw), UNIFORM(cameraPitch)) + radius;
    }

    OUTACCESS(o1) = thisrvA;
    OUTACCESS(o2) = thisrvB;
    OUTACCESS(o3) = thisrvC;

    OUTACCESS(prio) = thisPriority;
    OUTACCESS(dis) = thisDistance;
  } else {
    OUTACCESS(o1) = NEWVEC(ivec4)(0);
    OUTACCESS(o2) = NEWVEC(ivec4)(0);
    OUTACCESS(o3) = NEWVEC(ivec4)(0);
    OUTACCESS(prio) = 0;
    OUTACCESS(dis) = 0;
  }
}

void add_face_prio_distance(GLOBAL_ARGS SHARED_ARGS uint localId, modelinfo minfo, ivec4 thisrvA, ivec4 thisrvB, ivec4 thisrvC, int thisPriority, int thisDistance, ivec4 pos) {
  if (localId < minfo.size) {
    // if the face is not culled, it is calculated into priority distance averages
    if (face_visible(PASS_GLOBALS thisrvA, thisrvB, thisrvC, pos)) {
      atomicAdd(SHARED(totalNum)[thisPriority], 1);
      atomicAdd(SHARED(totalDistance)[thisPriority], thisDistance);

      // calculate minimum distance to any face of priority 10 for positioning the 11 faces later
      if (thisPriority == 10) {
        atomicMin(SHARED(min10), thisDistance);
      }
    }
  }
}

int map_face_priority(SHARED_ARGS uint localId, modelinfo minfo, int thisPriority, int thisDistance, OUTARG(int, prio)) {
  int size = minfo.size;

  // Compute average distances for 0/2, 3/4, and 6/8

  if (localId < size) {
    int avg1 = 0;
    int avg2 = 0;
    int avg3 = 0;

    if (SHARED(totalNum)[1] > 0 || SHARED(totalNum)[2] > 0) {
      avg1 = (SHARED(totalDistance)[1] + SHARED(totalDistance)[2]) / (SHARED(totalNum)[1] + SHARED(totalNum)[2]);
    }

    if (SHARED(totalNum)[3] > 0 || SHARED(totalNum)[4] > 0) {
      avg2 = (SHARED(totalDistance)[3] + SHARED(totalDistance)[4]) / (SHARED(totalNum)[3] + SHARED(totalNum)[4]);
    }

    if (SHARED(totalNum)[6] > 0 || SHARED(totalNum)[8] > 0) {
      avg3 = (SHARED(totalDistance)[6] + SHARED(totalDistance)[8]) / (SHARED(totalNum)[6] + SHARED(totalNum)[8]);
    }

    int adjPrio = priority_map(thisPriority, thisDistance, SHARED(min10), avg1, avg2, avg3);
    int prioIdx = atomicAdd(SHARED(totalMappedNum)[adjPrio], 1);

    OUTACCESS(prio) = adjPrio;
    return prioIdx;
  }

  OUTACCESS(prio) = 0;
  return 0;
}

void insert_dfs(SHARED_ARGS uint localId, modelinfo minfo, int adjPrio, int distance, int prioIdx) {
  int size = minfo.size;

  if (localId < size) {
    // calculate base offset into dfs based on number of faces with a lower priority
    int baseOff = count_prio_offset(PASS_SHARED adjPrio);
    // store into face array offset array by unique index
    SHARED(dfs)[baseOff + prioIdx] = (NEWVEC(int)(localId) << 16) | distance;
  }
}

void sort_and_insert(GLOBAL_ARGS SHARED_ARGS uint localId, modelinfo minfo, int thisPriority, int thisDistance, ivec4 thisrvA, ivec4 thisrvB, ivec4 thisrvC) {
  /* compute face distance */
  int size = minfo.size;

  if (localId < size) {
    int outOffset = minfo.idx;
    int uvOffset = minfo.uvOffset;
    int flags = minfo.flags;
    ivec4 pos = NEWVEC(ivec4)(minfo.x, minfo.y, minfo.z, 0);

    const int priorityOffset = count_prio_offset(PASS_SHARED thisPriority);
    const int numOfPriority = SHARED(totalMappedNum)[thisPriority];
    int start = priorityOffset; // index of first face with this priority
    int end = priorityOffset + numOfPriority; // index of last face with this priority
    int myOffset = priorityOffset;

    // we only have to order faces against others of the same priority
    // calculate position this face will be in
    for (int i = start; i < end; ++i) {
      int d1 = SHARED(dfs)[i];
      int theirId = d1 >> 16;
      int theirDistance = d1 & 0xffff;

      // the closest faces draw last, so have the highest index
      // if two faces have the same distance, the one with the
      // higher id draws last
      if ((theirDistance > thisDistance)
        || (theirDistance == thisDistance && theirId < localId)) {
        ++myOffset;
      }
    }

    // position vertices in scene and write to out buffer
    vout[outOffset + myOffset * 3]     = pos + thisrvA;
    vout[outOffset + myOffset * 3 + 1] = pos + thisrvB;
    vout[outOffset + myOffset * 3 + 2] = pos + thisrvC;

    if (uvOffset < 0) {
      uvout[outOffset + myOffset * 3]     = NEWVEC(vec4)(0, 0, 0, 0);
      uvout[outOffset + myOffset * 3 + 1] = NEWVEC(vec4)(0, 0, 0, 0);
      uvout[outOffset + myOffset * 3 + 2] = NEWVEC(vec4)(0, 0, 0, 0);
    } else if (flags >= 0) {
      uvout[outOffset + myOffset * 3]     = tempuv[uvOffset + localId * 3];
      uvout[outOffset + myOffset * 3 + 1] = tempuv[uvOffset + localId * 3 + 1];
      uvout[outOffset + myOffset * 3 + 2] = tempuv[uvOffset + localId * 3 + 2];
    } else {
      uvout[outOffset + myOffset * 3]     = uv[uvOffset + localId * 3];
      uvout[outOffset + myOffset * 3 + 1] = uv[uvOffset + localId * 3 + 1];
      uvout[outOffset + myOffset * 3 + 2] = uv[uvOffset + localId * 3 + 2];
    }
  }
}
