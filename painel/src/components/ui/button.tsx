import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "cn"
import { Slot } from "radix-ui"

const buttonVariants = cva(
  "group/button inline-flex shrink-0 items-center justify-center border border-transparent bg-clip-padding text-sm font-medium whitespace-nowrap transition-all outline-none select-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/50 active:not-aria-[haspopup]:translate-y-px disabled:pointer-events-none disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-2 aria-invalid:ring-destructive/20 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
  {
    variants: {
      variant: {
        default: "bg-primary text-primary-foreground hover:bg-primary-active",
        outline:
          "border-border-strong bg-surface text-foreground hover:bg-surface-raised aria-expanded:bg-surface-raised aria-expanded:text-foreground",
        secondary:
          "bg-surface-sunken text-ink hover:bg-surface-raised",
        ghost:
          "hover:bg-muted hover:text-foreground aria-expanded:bg-muted aria-expanded:text-foreground dark:hover:bg-muted/50",
        destructive:
          "bg-danger-soft text-danger hover:bg-danger-fill hover:text-white focus-visible:border-danger/40 focus-visible:ring-danger/20",
        link: "text-primary-ink underline-offset-4 hover:underline",
        "trading-up": "bg-success-fill text-[#0b0e11] hover:bg-success-fill/90",
        "trading-down": "bg-danger-fill text-white hover:bg-danger-fill/90",
        tertiary: "bg-transparent hover:underline text-foreground",
      },
      size: {
        default: "h-9 px-4 gap-2 rounded-md",
        pill: "h-12 px-8 rounded-pill",
        sm: "h-8 px-3 gap-1.5 rounded-md text-xs",
        lg: "h-12 px-8 rounded-md",
        icon: "size-9 rounded-md",
        "icon-sm": "size-8 rounded-sm",
        "icon-lg": "size-12 rounded-md",
        subscribe: "h-7 px-3 rounded-md text-xs",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
)

function Button({
  className,
  variant = "default",
  size = "default",
  asChild = false,
  ...props
}: React.ComponentProps<"button"> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean
  }) {
  const Comp = asChild ? Slot.Root : "button"

  return (
    <Comp
      data-slot="button"
      data-variant={variant}
      data-size={size}
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    />
  )
}

export { Button, buttonVariants }
