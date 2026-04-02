/*
 * geometry.c - Implementation of the geometry library
 *
 * This is plain C with zero Java dependencies.
 * Compile: gcc -shared -fPIC -o libgeometry.so geometry.c -lm
 */

#include "geometry.h"
#include <math.h>

double circle_area(const Circle *c) {
    return PI * c->radius * c->radius;
}

double circle_perimeter(const Circle *c) {
    return 2.0 * PI * c->radius;
}

double rectangle_area(const Rectangle *r) {
    double width = fabs(r->bottomRight.x - r->topLeft.x);
    double height = fabs(r->bottomRight.y - r->topLeft.y);
    return width * height;
}

double rectangle_perimeter(const Rectangle *r) {
    double width = fabs(r->bottomRight.x - r->topLeft.x);
    double height = fabs(r->bottomRight.y - r->topLeft.y);
    return 2.0 * (width + height);
}

double triangle_area(const Triangle *t) {
    /* Using the cross product formula: 0.5 * |AB x AC| */
    double ax = t->vertices[1].x - t->vertices[0].x;
    double ay = t->vertices[1].y - t->vertices[0].y;
    double bx = t->vertices[2].x - t->vertices[0].x;
    double by = t->vertices[2].y - t->vertices[0].y;
    return 0.5 * fabs(ax * by - ay * bx);
}

double point_distance(const Point2D *a, const Point2D *b) {
    double dx = b->x - a->x;
    double dy = b->y - a->y;
    return sqrt(dx * dx + dy * dy);
}

Point2D point_translate(const Point2D *p, double dx, double dy) {
    Point2D result;
    result.x = p->x + dx;
    result.y = p->y + dy;
    return result;
}

int point_in_circle(const Point2D *p, const Circle *c) {
    double dist = point_distance(p, &c->center);
    return dist <= c->radius ? 1 : 0;
}
